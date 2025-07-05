var isAllExpanded = false;
const OPEN_ICON   = '<i class="fas fa-angle-up fa-lg"></i>';
const CLOSED_ICON = '<i class="fas fa-angle-right fa-lg"></i>';

function syncOverallArrow() {
  const innerDivs = document.querySelectorAll("[id^='inner-div-']");
  const anyOpen   = Array.from(innerDivs).some(div => div.style.display !== "none");

  const icon = anyOpen ? OPEN_ICON : CLOSED_ICON;

  const arrowMain   = document.getElementById("toggle-all");
  const arrowBottom = document.getElementById("toggle-all-btm");

  if (arrowMain)   arrowMain.innerHTML   = icon;
  if (arrowBottom) arrowBottom.innerHTML = icon;

  isAllExpanded = anyOpen;
}

function toggleInnerDiv(rowId) {
  const innerDiv = document.getElementById(`inner-div-${rowId}`);
  const arrowButton = document.getElementById(`arrow-button-${rowId}`);
  const arrowButtonMain = document.getElementById("toggle-all");
  const arrowButtonBtm = document.getElementById("toggle-all-btm");

  const isCollapsed = innerDiv.style.display === "none";
  const symbol = isCollapsed ? OPEN_ICON : CLOSED_ICON;

  innerDiv.style.display = isCollapsed ? "block" : "none";
  arrowButton.innerHTML = symbol;

  if (isCollapsed) {
    arrowButtonMain.innerHTML = symbol;
    arrowButtonBtm.innerHTML = symbol;
    isAllExpanded = true;
  }
  syncOverallArrow();
}

function toggleAll() {
  const innerDivs = document.querySelectorAll("[id^='inner-div-']");
  const arrowButtons = document.querySelectorAll("[id^='arrow-button-']");
  const arrowButtonMain = document.getElementById("toggle-all");
  const arrowButtonBtm = document.getElementById("toggle-all-btm");

  const expand = !isAllExpanded;
  const displayStyle = expand ? "block" : "none";
  const icon = expand ? OPEN_ICON : CLOSED_ICON;

  innerDivs.forEach(div => div.style.display = displayStyle);

  arrowButtons.forEach(btn => btn.innerHTML = icon);

  if (arrowButtonMain) arrowButtonMain.innerHTML = icon;
  if (arrowButtonBtm) arrowButtonBtm.innerHTML = icon;

  isAllExpanded = expand;
  syncOverallArrow();
}

function downloadReport() {
  const type = document.getElementById('exportType').value;
  if (type === 'image') {
    downloadReportImage();
  } else if (type === 'excel') {
    downloadReportExcel();
  }
}
(function prepareFeatureTables () {

  // STEP‑1  ──────────────────────────────────────────────────────────────
  document.querySelectorAll(".row").forEach(row => {
    const featureCell = row.querySelector(".span1.feature");
    const table       = row.querySelector(".scenario-table");
    if (featureCell && table) {
      const featureName = featureCell.textContent.trim();
      table.dataset.featureName = featureName;       // <‑‑ for XLSX sheet name
    }
  });

  // STEP‑2  ──────────────────────────────────────────────────────────────
  // Hook into the Excel export so we humanise the dot → text
  window.prepareTableForExcel = table => {
    table.querySelectorAll(".circle-tc").forEach(dot => {
      const td   = dot.parentElement;
      const txt  = dot.classList.contains("green") ? "Passed"
                : dot.classList.contains("red")   ? "Failed"
                : dot.classList.contains("cyan")  ? "Skipped"
                : "—";
      td.textContent = txt;                        // replace dot with word
    });
  };

})();
function downloadReportExcel () {
  const cleanName = raw => raw.replace(/[\[\]\*\/\\\?:]/g, ' ').trim().substring(0, 31) || 'Sheet';
  const wb = XLSX.utils.book_new();
  const sheets = {};

  document.querySelectorAll(".scenario-table").forEach((table) => {
    const rawName = table.dataset.featureName || 'Feature';
    const sheetName = cleanName(rawName);

    if (!sheets[sheetName]) {
      const outerRow = table.closest('.row');
      const passed   = outerRow.querySelector('.span2.passed')?.textContent.trim()   || '0';
      const failed   = outerRow.querySelector('.span2.failed')?.textContent.trim()   || '0';
      const skipped  = outerRow.querySelector('.span2.skipped')?.textContent.trim()  || '0';
      const total    = outerRow.querySelector('.span2.total')?.textContent.trim()    || '0';
      const percent  = outerRow.querySelector('.span2.pass-percent')?.textContent.trim() || '';
      const duration = document.querySelector('.duration-cell')?.textContent.trim() || '—';

      const summary = [
        ['Summary'],
        ['Passed',  passed],
        ['Failed',  failed],
        ['Skipped', skipped],
        ['Total',   total],
        ['Pass (%)', percent],
        ['Duration', duration],
        [''] // Spacer
      ];

      sheets[sheetName] = XLSX.utils.aoa_to_sheet(summary);
    }

    const cleanTable = table.cloneNode(true);
    cleanTable.querySelectorAll("tr").forEach(row => {
      row.querySelectorAll("td.hidden, td.hide-in-mobile, th.hidden, th.hide-in-mobile")
         .forEach(cell => cell.remove());
    });

    cleanTable.querySelectorAll(".circle-tc").forEach(dot => {
      const td = dot.parentElement;
      td.textContent = dot.classList.contains("green") ? "Passed"
                   : dot.classList.contains("red")   ? "Failed"
                   : dot.classList.contains("cyan")  ? "Skipped"
                   : "—";
    });

    cleanTable.querySelectorAll("td").forEach(td => {
      td.innerHTML = td.innerHTML.replace(/<br\s*\/?>/gi, '\n');
    });

    const jsonRows = XLSX.utils.sheet_to_json(
      XLSX.utils.table_to_sheet(cleanTable),
      { header: 1 }
    );

    const ws = sheets[sheetName];
    const startRow = XLSX.utils.decode_range(ws['!ref']).e.r + 2;

    XLSX.utils.sheet_add_aoa(ws, jsonRows, { origin: { r: startRow, c: 0 } });

    // Auto column width
    const colWidths = jsonRows.reduce((widths, row) => {
      row.forEach((val, idx) => {
        const len = (val?.toString()?.length || 0);
        widths[idx] = Math.max(widths[idx] || 10, len);
      });
      return widths;
    }, []);
    ws['!cols'] = colWidths.map(w => ({ wch: w + 2 }));
  });

  Object.entries(sheets).forEach(([name, ws]) => {
    XLSX.utils.book_append_sheet(wb, ws, name);
  });

  const ts = new Date().toISOString().replace(/[:T]/g, '-').split('.')[0];
  XLSX.writeFile(wb, `CucumberTestSummary_${ts}.xlsx`);
}

function downloadReportImage () {
  const target  = document.querySelector('.container');
  const select  = document.getElementById('exportType');   // ⬅ dropdown
  const origSelOpacity = select.style.opacity;            // save style
  const origWidth = target.style.width;                   // save width

  /* ── 1. temporarily hide the native select ─────────────────────────── */
  select.style.opacity = '0';

  /* ── 2. make container wide enough so nothing is clipped ───────────── */
  target.style.width = target.scrollWidth + 'px';

  /* ── 3. capture ─────────────────────────────────────────────────────── */
  html2canvas(target, { scale: 2 }).then(canvas => {

    /* ── 4. restore UI styles ─────────────────────────────────────────── */
    select.style.opacity = origSelOpacity || '';
    target.style.width   = origWidth      || '';

    /* ── 5. trigger PNG download ──────────────────────────────────────── */
    canvas.toBlob(blob => {
      const url = URL.createObjectURL(blob);
      const ts  = getTimestamp();
      const a   = document.createElement('a');
      a.href = url;
      a.download = `CucumberTestSummary_${ts}.png`;
      a.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  });
}
function getTimestamp() {
  const now = new Date();
  return now.toISOString()
            .replace(/T/, '_')
            .replace(/:/g, '-')
            .replace(/\..+/, '');
}

const pass   = $overallPassCount;
const fail   = $overallFailCount;
const skipped = $overallSkipCount;
const total  = $overallCount;

const percentage = ((pass / total) * 100).toFixed(0) + '%';

const centerTextPlugin = {
  id: 'centerText',
  afterDraw(chart) {
    const { ctx, width, height } = chart;

    const centerX = width / 2;
    const centerY = height / 2;
    const mainSize = Math.round(width / 8);
    const subSize  = Math.round(width / 18);

    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    ctx.font = `bold ${mainSize}px sans-serif`;
    ctx.fillStyle = '#000';
    ctx.fillText(percentage, centerX + 5, centerY - subSize);

    ctx.font = `${subSize}px sans-serif`;
    ctx.fillStyle = '#555';
    ctx.fillText(`${pass}/${total} Passed`, centerX + 3, centerY + subSize * 1.2);

    ctx.restore();
  }
};

const config = {
  type: 'doughnut',
  data: {
    labels: ['Pass', 'Fail', 'Skipped'],
    datasets: [{
      data: [pass, fail, skipped],
      backgroundColor: ['#00B000', '#FF3030', '#88AAFF'],
      hoverOffset: 5
    }]
  },
  options: {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '55%',
    layout: { padding: 5},
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label(ctx) {
            const total = ctx.dataset.data.reduce((s, v) => s + v, 0);
            const pct = ((ctx.raw / total) * 100).toFixed(2);
            return `${ctx.label}: ${ctx.raw} (${pct}%)`;
          },
          title() { return ''; }
        },
        bodyFont: { size: 14 },
        padding: 10
      }
    }
  },
  plugins: [centerTextPlugin]
};

const myChart = new Chart(document.getElementById('myPieChart'), config);

function filterScenarios() {
  if (!isAllExpanded) toggleAll();

  const active = Array.from(document.querySelectorAll(".status-filter:checked"))
                      .map(cb => cb.value);

  const tables = document.querySelectorAll(".inner-div table.dataTable");

  tables.forEach(table => {
    let visible = 0;
    const rows = table.querySelectorAll("tr:not(.data-heading)");

    rows.forEach(row => {
      const dot = row.querySelector(".circle-tc");
      if (!dot) return;

      const status = dot.classList.contains("green") ? "green" :
                     dot.classList.contains("red")   ? "red"   :
                     dot.classList.contains("cyan")  ? "cyan"  : "";

      const show = active.includes(status);
      row.style.display = show ? "" : "none";
      if (show) visible++;
    });

    const featureRow = table.closest(".row");
    featureRow.style.display = visible ? "flex" : "none";
  });
}

function resetFilter() {
  document.querySelectorAll(".status-filter").forEach(cb => cb.checked = true);
  filterScenarios();
  if (isAllExpanded) {
      toggleAll();
    }
}

const bar = document.querySelector(".filter-bar");
if (bar) bar.addEventListener("change", e => {
  if (e.target.matches(".status-filter")) filterScenarios();
});

document.querySelectorAll('.scenario-table').forEach(table => {
  const cols = table.querySelectorAll('col').length;
  table.classList.add(`scenario-table-${cols}`); // scenario-table-3 or -4
});

document.querySelectorAll('.ellipsis-cell').forEach(td => {
  if (!td.hasAttribute('title') || !td.getAttribute('title').trim()) {
    td.setAttribute('title', td.textContent.trim());
  }
});

document.querySelectorAll('.ellipsis-cell').forEach(td => {
  if (!td.hasAttribute('title') || !td.getAttribute('title').trim()) {
    td.setAttribute('title', td.textContent.trim());
  }
});

document.addEventListener('DOMContentLoaded', () => {

  /* Check each class independently */
  ['credential-feat', 'credential-sc'].forEach(cls => hideIfAllEmpty(cls));

  /* ------------------------------------------------------------------ */
  /*  Hide all nodes with the given class if every one is a placeholder */
  /* ------------------------------------------------------------------ */
  function hideIfAllEmpty(cls) {
    /* Grab ANY element bearing that class (td, span, div—doesn’t matter) */
    const nodes = Array.from(document.querySelectorAll(`.${cls}`));
    if (nodes.length === 0) return;            // none on the page → nothing to do

    /* Are they *all* empty? */
    const allEmpty = nodes.every(isPlaceholder);

    /* If yes, add .hidden to each one */
    if (allEmpty) nodes.forEach(n => n.classList.add('hidden'));
  }

  /* ----------------------------------------- */
  /*  Decide whether a single node is “empty”  */
  /* ----------------------------------------- */
  function isPlaceholder(node) {
    const text = node.textContent.replace(/\s+/g, '').toLowerCase();

    return (
      text === '' ||                                 // truly blank
      text === 'testcredentials' ||                  // literal wording
      text === 'credentials' ||                  // literal wording
      /^-+$/.test(text)                              // one or more dashes only
    );
  }
});