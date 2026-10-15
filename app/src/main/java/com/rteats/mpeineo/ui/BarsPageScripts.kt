package com.rteats.mpeineo.ui

import android.webkit.JavascriptInterface

/** Official BARS page recognition and structured marks extraction. */
internal const val BARS_JS_INTERFACE = "MpeiNeoBars"
internal const val BARS_EXTRACT_TOKEN_PLACEHOLDER = "__MPEI_NEO_EXTRACT_TOKEN__"

internal val BARS_STOP_STATE_OBSERVER_SCRIPT = """
    (function() {
        if (window.__mpeiNeoBarsStateObserver) {
            window.__mpeiNeoBarsStateObserver.disconnect();
            window.__mpeiNeoBarsStateObserver = null;
        }
        if (window.__mpeiNeoBarsStateDebounce) {
            clearTimeout(window.__mpeiNeoBarsStateDebounce);
            window.__mpeiNeoBarsStateDebounce = null;
        }
        if (window.__mpeiNeoBarsStateTimer) {
            clearInterval(window.__mpeiNeoBarsStateTimer);
            window.__mpeiNeoBarsStateTimer = null;
        }
    })();
""".trimIndent()

internal class BarsJavascriptBridge(
    private val onPageState: (String) -> Unit,
    private val onData: (Int, String) -> Unit,
    private val onExtractionError: (Int, String) -> Unit,
    private val onError: (String) -> Unit,
    private val onRawSnapshot: (String, String) -> Unit = { _, _ -> },
    private val onSummaryDiagnostic: (String) -> Unit = {},
) {
    @JavascriptInterface
    fun onPageState(json: String) = onPageState.invoke(json)

    @JavascriptInterface
    fun onData(token: Int, json: String) = onData.invoke(token, json)

    @JavascriptInterface
    fun onExtractionError(token: Int, message: String) = onExtractionError.invoke(token, message)

    @JavascriptInterface
    fun onError(message: String) = onError.invoke(message)

    @JavascriptInterface
    fun onRawSnapshot(page: String, rawText: String) = onRawSnapshot.invoke(page, rawText)

    @JavascriptInterface
    fun onSummaryDiagnostic(message: String) = onSummaryDiagnostic.invoke(message)
}

internal val BARS_PAGE_STATE_SCRIPT = """
    (function() {
        // If injected again into the same document, replace the old observer.
        if (window.__mpeiNeoBarsStateObserver) {
            window.__mpeiNeoBarsStateObserver.disconnect();
        }
        if (window.__mpeiNeoBarsStateDebounce) {
            clearTimeout(window.__mpeiNeoBarsStateDebounce);
            window.__mpeiNeoBarsStateDebounce = null;
        }
        if (window.__mpeiNeoBarsStateTimer) {
            clearInterval(window.__mpeiNeoBarsStateTimer);
            window.__mpeiNeoBarsStateTimer = null;
        }

        let lastSignature = null;
        function reportState() {
            try {
                const path = (window.location.pathname || "").toLowerCase();
                const hasPassword = !!document.querySelector('input[type="password"]');
                const marks = !!document.getElementById("div-Student_SemesterSheet__Mark");
                const summaryRoute = path.indexOf("/main/summary") >= 0;
                const markMatrix = document.getElementById("tableMarkSummary");
                const attendance = document.getElementById("tableSkipSummary");
                // Small cohorts can have fewer than four table rows. A real
                // rendered data row (header + at least one populated row) is
                // sufficient; the AJAX observer keeps waiting otherwise.
                function hasReportData(table) {
                    if (!table) return false;
                    const rows = Array.from(table.querySelectorAll("tr"));
                    return rows.length >= 2 && rows.some(function(row, index) {
                        return index > 0 && Array.from(row.querySelectorAll("td, th"))
                            .some(function(cell) { return !!(cell.textContent || "").trim(); });
                    });
                }
                const summaryReady = summaryRoute &&
                    hasReportData(markMatrix) && hasReportData(attendance);
                const studentList = path.indexOf("/student/liststudent") >= 0;
                const hasOneTimeCodeInput = !!document.querySelector(
                    'input[autocomplete="one-time-code"], ' +
                    'input[name*="code" i], input[id*="code" i]'
                );
                // Text search is a fallback for BARS' custom 2FA modal.
                const bodyText = hasOneTimeCodeInput ? "" :
                    ((document.body && document.body.textContent) || "").toLowerCase();
                const hasTwoFactorUi =
                    hasOneTimeCodeInput ||
                    (
                        (bodyText.indexOf("двухфактор") >= 0 ||
                         bodyText.indexOf("код подтверждения") >= 0 ||
                         bodyText.indexOf("получить код") >= 0 ||
                         bodyText.indexOf("одноразов") >= 0) &&
                        !!document.querySelector("form, .modal, [role='dialog']")
                    );
                const payload = {
                    url: window.location.href,
                    path: path,
                    isLoginPage: hasPassword,
                    isAuthFlow: path.indexOf("/auth") >= 0 || hasTwoFactorUi,
                    isStudentList: studentList,
                    isMarksPage: marks,
                    isSummaryPage: summaryRoute,
                    isSummaryReady: summaryReady
                };
                const signature = JSON.stringify(payload);
                if (signature !== lastSignature) {
                    lastSignature = signature;
                    MpeiNeoBars.onPageState(signature);
                }
                if ((marks || summaryReady) && window.__mpeiNeoBarsStateObserver) {
                    window.__mpeiNeoBarsStateObserver.disconnect();
                    window.__mpeiNeoBarsStateObserver = null;
                }
            } catch (error) {
                MpeiNeoBars.onError("Не удалось определить страницу БАРС");
            }
        }

        // Only inspect DOM changes, not once per second forever.
        // Debounce bursts from BARS' AJAX page rendering.
        const observer = new MutationObserver(function() {
            if (window.__mpeiNeoBarsStateDebounce) return;
            window.__mpeiNeoBarsStateDebounce = setTimeout(function() {
                window.__mpeiNeoBarsStateDebounce = null;
                reportState();
            }, 180);
        });
        window.__mpeiNeoBarsStateObserver = observer;
        if (document.documentElement) {
            observer.observe(document.documentElement, {
                childList: true, subtree: true, characterData: true,
                attributes: true, attributeFilter: ["class", "type"]
            });
        }
        reportState();
    })();
""".trimIndent()

/*
 * DOM extraction is based on the same public BARS markup used by MpeiX's former
 * statics/bars/extract.js implementation, but is intentionally self-contained here.
 */
internal val BARS_EXTRACT_SCRIPT = """
    (function() {
        // This token belongs to this specific script execution, not the page globally.
        const extractionToken = __MPEI_NEO_EXTRACT_TOKEN__;
        function clean(value) {
            return (value || "").replace(/\s+/g, " ").trim();
        }

        function parseRow(row) {
            const columns = Array.from(row.querySelectorAll("td")).map(function(cell) {
                return clean(cell.textContent);
            });

            if (columns.length === 0) return { type: "UNDEFINED" };

            if (columns.length === 4) {
                return {
                    type: "CONTROL_ACTIVITY",
                    name: clean(columns[0].replace(/[(]критерии[)]\s*$/i, "")),
                    weight: columns[1] || null,
                    weekNum: columns[2] || null,
                    markAndDate: columns[3] || null
                };
            }

            const title = columns[0] || "";
            if (title.indexOf("Балл текущего") === 0) {
                return {
                    type: "CURRENT_SCORE",
                    name: title,
                    markAndDate: columns[1] || null
                };
            }
            if (title.indexOf("Контрольная неделя №") === 0) {
                return {
                    type: "CONTROL_WEEK",
                    name: title,
                    weekNum: columns[1] || null,
                    markAndDate: columns[2] || null
                };
            }
            if (title.indexOf("Промежуточная") === 0) {
                return {
                    type: "INTERMEDIATE_MARK",
                    name: title,
                    markAndDate: columns[1] || null
                };
            }
            if (title.indexOf("Итоговая") === 0) {
                return {
                    type: "FINAL_MARK",
                    name: title,
                    markAndDate: columns[1] || null
                };
            }
            return { type: "UNDEFINED", name: title };
        }

        function extract(attempt) {
            try {
                const marksContainer =
                    document.getElementById("div-Student_SemesterSheet__Mark");

                if (!marksContainer) {
                    if (attempt < 8) {
                        window.setTimeout(function() { extract(attempt + 1); }, 400);
                        return;
                    }
                    MpeiNeoBars.onExtractionError(
                        extractionToken, "Страница БАРС загрузилась, но таблица оценок не найдена."
                    );
                    return;
                }

                const blocks = Array.from(marksContainer.children || []);
                const disciplines = [];

                for (let i = 0; i < blocks.length; i++) {
                    const block = blocks[i];
                    if (!block.classList || !block.classList.contains("collapse") || i === 0) {
                        continue;
                    }

                    const headerParts = clean(blocks[i - 1].textContent)
                        .split(",")
                        .map(clean)
                        .filter(Boolean);

                    const assessmentIndex = headerParts.findLastIndex(function(part) {
                        return /(экзамен|зач[её]т|аттестаци|дифференц)/i.test(part);
                    });

                    let disciplineName = "";
                    let personName = "";
                    let assessmentType = "";

                    if (assessmentIndex > 0) {
                        personName = headerParts[assessmentIndex - 1] || "";
                        assessmentType = headerParts[assessmentIndex] || "";
                        disciplineName = headerParts
                            .slice(0, Math.max(assessmentIndex - 1, 1))
                            .join(", ");
                    } else if (headerParts.length >= 4) {
                        disciplineName = headerParts.slice(0, -3).join(", ");
                        personName = headerParts[headerParts.length - 3] || "";
                        assessmentType = headerParts[headerParts.length - 2] || "";
                    } else {
                        disciplineName = headerParts[0] || "";
                        personName = headerParts[1] || "";
                        assessmentType = headerParts[2] || "";
                    }

                    const activities = Array.from(block.querySelectorAll("tr"))
                        .filter(function(row) {
                            return !row.classList.contains("collapse");
                        })
                        .map(parseRow);

                    disciplines.push({
                        disciplineName: clean(disciplineName),
                        personName: clean(personName),
                        assessmentType: clean(assessmentType),
                        activities: activities
                    });
                }

                const metadata =
                    document.querySelector("#div-FormHeader .row .col-sm") ||
                    document.querySelector("#div-FormHeader .form-row .col-sm");

                let name = "";
                let group = "";

                if (metadata) {
                    const lines = Array.from(metadata.children || [])
                        .map(function(node) { return clean(node.textContent); })
                        .filter(Boolean);

                    if (lines.length > 0) {
                        const combined = lines[0].match(/^(.*?)\s*\((.*?)\)\s*$/);
                        if (combined) {
                            name = clean(combined[1]);
                            group = clean(combined[2]);
                        } else {
                            name = clean(lines[0].replace(/\(.*\)$/g, ""));
                            if (lines.length > 1) group = clean(lines[1]);
                        }
                    }
                }

                // Collect labelled, visible student fields, never hidden form
                // inputs, passwords, cookies or arbitrary body content.
                const studentDetails = [];
                const allowed = /^(институт|факультет|курс|форма обучения|направление|специальность|профиль|кафедра|статус|уровень образования|номер зачетной книжки|зач[её]тная книжка|группа|семестр)$/i;
                function addField(rawLabel, rawValue) {
                    const label = clean(rawLabel).replace(/:$/, "");
                    const value = clean(rawValue);
                    if (!allowed.test(label) || !value || value.length > 140 ||
                        studentDetails.some(function(field) { return field.label === label; })) return;
                    studentDetails.push({ label: label, value: value });
                }
                ["#div-FormHeader", "#div-StudentInfo", "#div-Student_Info",
                    ".student-info", ".student-profile"].forEach(function(selector) {
                    const section = document.querySelector(selector);
                    if (!section) return;
                    Array.from(section.querySelectorAll("tr")).forEach(function(row) {
                        const cells = Array.from(row.querySelectorAll("th, td"));
                        if (cells.length === 2) addField(cells[0].textContent, cells[1].textContent);
                    });
                    Array.from(section.querySelectorAll("dt")).forEach(function(dt) {
                        if (dt.nextElementSibling && dt.nextElementSibling.tagName === "DD")
                            addField(dt.textContent, dt.nextElementSibling.textContent);
                    });
                    Array.from(section.querySelectorAll("label")).forEach(function(label) {
                        const next = label.nextElementSibling;
                        if (next && !next.matches("input, select, textarea"))
                            addField(label.textContent, next.textContent);
                    });
                });
                const semesterNode =
                    document.querySelector(".filter-option-inner-inner");
                const semester = semesterNode ? clean(semesterNode.textContent) : "";

                // The container may arrive before AJAX has populated its subjects.
                if (disciplines.length === 0) {
                    if (attempt < 8) {
                        window.setTimeout(function() { extract(attempt + 1); }, 400);
                        return;
                    }
                    MpeiNeoBars.onExtractionError(
                        extractionToken, "БАРС не вернул ни одной дисциплины."
                    );
                    return;
                }

                MpeiNeoBars.onData(extractionToken, JSON.stringify({
                    name: name,
                    group: group,
                    semester: semester,
                    studentDetails: studentDetails.slice(0, 18),
                    disciplines: disciplines
                }));
            } catch (error) {
                MpeiNeoBars.onExtractionError(
                    extractionToken, "Не удалось прочитать оценки из текущей сессии БАРС."
                );
            }
        }

        extract(0);
    })();
""".trimIndent()

/**
 * Resolve the REAL per-student URLs from BARS' rendered navigation menu.
 * The current site requires ?studentID=... for Summary and Main.
 * This is run only on the authenticated Main page. The ID never goes to logs.
 */
internal val BARS_AUTHENTICATED_ROUTES_SCRIPT = """
(function() {
    const mainPath = '/bars_web/ST_Study/Main/Main';
    const summaryPath = '/bars_web/ST_Study/Main/Summary';
    const page = new URL(window.location.href);
    const found = {
        main: '', summary: '', source: 'missing', currentParam: false,
        scopedLinks: 0, distinctIds: 0, mainLinks: 0, summaryLinks: 0
    };

    // Only data from the trusted, authenticated BARS study document counts.
    if (page.protocol !== 'https:' || page.hostname.toLowerCase() !== 'bars.mpei.ru' ||
        page.port || page.username || page.password || page.pathname !== mainPath) {
        return JSON.stringify(found);
    }

    const origin = page.origin;
    const studentIds = new Set();
    const profileIds = new Set();
    const mainLinks = new Map();
    const summaryLinks = new Map();

    function observedId(url) {
        if (url.origin !== origin ||
            (!url.pathname.startsWith('/bars_web/ST_Study/') &&
             url.pathname !== '/bars_web/ST/Student/StudentLifeCycle')) return null;
        const ids = url.searchParams.getAll('studentID');
        if (ids.length !== 1 || !/^[a-z0-9-]{1,128}$/i.test(ids[0])) return null;
        return ids[0];
    }

    const currentId = observedId(page);
    found.currentParam = !!currentId;
    document.querySelectorAll('a[href]').forEach(function(anchor) {
        try {
            const url = new URL(anchor.getAttribute('href'), page.href);
            const id = observedId(url);
            if (!id) return;
            if (url.pathname === '/bars_web/ST/Student/StudentLifeCycle') {
                profileIds.add(id);
                return;
            }
            found.scopedLinks++;
            studentIds.add(id);
            url.hash = '';
            if (url.pathname === mainPath) {
                found.mainLinks++;
                mainLinks.set(id, url.href);
            } else if (url.pathname === summaryPath) {
                found.summaryLinks++;
                summaryLinks.set(id, url.href);
            }
        } catch (_) {}
    });
    const allIds = new Set([...studentIds, ...profileIds]);
    found.distinctIds = allIds.size;

    let selected = null;
    if (currentId) {
        selected = currentId;
        found.source = 'current-url';
    } else if (allIds.size === 1) {
        selected = allIds.values().next().value;
        found.source = mainLinks.has(selected) ? 'menu-main' :
            summaryLinks.has(selected) ? 'menu-summary' :
            profileIds.has(selected) ? 'profile-link' : 'scoped-link';
    } else if (allIds.size > 1) {
        // A page may list several students; never guess which is active.
        found.source = 'ambiguous';
    }
    if (!selected) return JSON.stringify(found);

    function route(path) {
        const url = new URL(path, origin);
        url.searchParams.set('studentID', selected);
        return url.href;
    }
    // Prefer the server's official links when present. Only synthesize the
    // matching route from a student ID actually observed on this document.
    found.main = mainLinks.get(selected) || (currentId ? (() => {
        const url = new URL(page.href); url.hash = ''; return url.href;
    })() : route(mainPath));
    found.summary = summaryLinks.get(selected) || route(summaryPath);
    return JSON.stringify(found);
})()
""".trimIndent()

/** Emit only safe structural diagnostics; never document text or query values. */
internal val BARS_SUMMARY_DIAGNOSTICS_SCRIPT = """
(function() {
    const grades = document.getElementById('tableMarkSummary');
    const attendance = document.getElementById('tableSkipSummary');
    const plain = ((document.body && document.body.textContent) || '')
        .slice(0, 8000).toLowerCase();
    const rejected = /invalid request|неверный запрос|некорректный запрос|ошибочный запрос/
        .test(plain);
    const queryPresent = new URLSearchParams(window.location.search).has('studentID');
    const rows = function(table) {
        return table ? table.querySelectorAll('tr').length : 0;
    };
    MpeiNeoBars.onSummaryDiagnostic(
        'route=summary studentParam=' + queryPresent +
        ' gradeRows=' + rows(grades) +
        ' attendanceRows=' + rows(attendance) +
        ' serverReject=' + rejected
    );
})()
""".trimIndent()
