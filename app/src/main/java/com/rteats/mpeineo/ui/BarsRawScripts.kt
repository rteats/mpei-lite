package com.rteats.mpeineo.ui

/*
 * Cache visible student profile indicators and educational tables for native BARS views.
 * Keep these captures independent from the official login flow.
 * No scripts, passwords, cookies, hidden inputs, credentials or page HTML.
 * The saved user-provided HTML defines these stable selectors.
 */
internal val BARS_MAIN_RAW_SCRIPT = """
(function() {
  const clean = s => (s || '').replace(/\s+/g, ' ').trim();
  const lines = ['Источник: /ST_Study/Main/Main'];
  const profile = document.querySelector('#div-FormHeader');
  if (profile) {
    lines.push('=== Профиль и сводные показатели ===');
    Array.from(profile.querySelectorAll('.list-inline-item')).forEach(item => {
      const value = clean(item.textContent);
      if (value && value.length <= 350) lines.push(value);
    });
    const name = profile.querySelector('.fw-bold');
    if (name) lines.unshift('Студент: ' + clean(name.textContent));
  }
  const modal = document.querySelector('#modalFormContent .modal-body');
  if (modal) {
    lines.push('=== Карточка студента (видимые поля) ===');
    Array.from(modal.querySelectorAll('.row')).forEach(row => {
      const text = clean(row.textContent);
      if (text && text.length < 330 && !/парол|токен|сеанс|cookie/i.test(text)) {
        lines.push(text);
      }
    });
  }
  const markContainer = document.getElementById('div-Student_SemesterSheet__Mark');
  if (markContainer) {
    lines.push('=== Дисциплины и контрольные мероприятия ===');
    const blocks = Array.from(markContainer.children || []);
    blocks.forEach((block, index) => {
      if (!block.classList || !block.classList.contains('collapse') || index < 1) return;
      const head = clean(blocks[index - 1].textContent);
      if (head) lines.push('Дисциплина: ' + head.slice(0, 500));
      Array.from(block.querySelectorAll('table')).forEach((table, tableIndex) => {
        lines.push('Таблица ' + (tableIndex + 1));
        Array.from(table.querySelectorAll('tr')).forEach((row, rowIndex) => {
          const cells = Array.from(row.querySelectorAll('th,td')).map(cell =>
            clean(cell.textContent).slice(0, 700));
          if (cells.some(Boolean)) lines.push((rowIndex + 1) + ': ' + cells.join(' | '));
        });
      });
    });
  }
  MpeiNeoBars.onRawSnapshot('MAIN', lines.join('\n').slice(0, 380000));
})();
""".trimIndent()

/** Page-specific real DOM, not guessed grades calculated from Main activities. */
internal val BARS_SUMMARY_RAW_SCRIPT = """
(function() {
  const clean = s => (s || '').replace(/\s+/g, ' ').trim();
  const grades = document.getElementById('tableMarkSummary');
  const attendance = document.getElementById('tableSkipSummary');
  function hasReportData(table) {
    if (!table) return false;
    const rows = Array.from(table.querySelectorAll('tr'));
    return rows.length >= 2 && rows.some((row, index) => index > 0 &&
      Array.from(row.querySelectorAll('td,th')).some(cell =>
        !!clean(cell.textContent)));
  }
  if (!hasReportData(grades) || !hasReportData(attendance)) return;
  const lines = ['Источник: /ST_Study/Main/Summary'];
  const semester = document.querySelector('#div-Summary .filter-option-inner-inner');
  if (semester) lines.push('Семестр: ' + clean(semester.textContent));
  function dumpTable(table, heading, maxRows) {
    lines.push('=== ' + heading + ' ===');
    Array.from(table.querySelectorAll('tr')).slice(0, maxRows).forEach((row, index) => {
      const cells = Array.from(row.querySelectorAll('th,td')).map(cell => {
        const value = clean(cell.textContent).slice(0, 400);
        const metadata = [];
        if (cell.colSpan > 1) metadata.push('colspan=' + cell.colSpan);
        if (cell.rowSpan > 1) metadata.push('rowspan=' + cell.rowSpan);
        if (cell.classList.contains('summary-td-current-week')) metadata.push('current-week');
        if (cell.querySelector('.text-danger, .text-success, .badge')) {
          metadata.push('mark-highlight');
        }
        return (value || '∅') + (metadata.length ? ' [' + metadata.join(',') + ']' : '');
      });
      lines.push((index + 1) + ': ' + cells.join(' | '));
    });
  }
  dumpTable(grades, 'Матрица оценок по неделям (ТБ, ПА, И)', 160);
  dumpTable(attendance, 'Посещаемость по журналу и учебному плану', 160);
  const notes = document.querySelectorAll(
      '#div-Student_SemesterSheet__MarkSummary .small, ' +
      '#div-Student_SemesterSheet__MarkSummary small, ' +
      '#div-AttendanceSheet_Student__SkipSummary .small');
  lines.push('=== Пояснения и примечания ===');
  Array.from(notes).forEach(node => {
    const text = clean(node.textContent);
    if (text && text.length < 1000) lines.push(text);
  });
  MpeiNeoBars.onRawSnapshot('SUMMARY', lines.join('\n').slice(0, 380000));
})();
""".trimIndent()
