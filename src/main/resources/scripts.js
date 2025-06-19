var isAllExpanded = false; // Keeps track of the toggle state
const OPEN_ICON   = '<i class="fas fa-angle-up fa-lg"></i>';
const CLOSED_ICON = '<i class="fas fa-angle-right fa-lg"></i>';

/**
 * Check if **any** inner‑div is visible.
 * If so → show “open” icon on the global toggle buttons;
 * otherwise → show “closed” icon.
 * Also refresh the `isAllExpanded` flag so toggleAll() stays in sync.
 */
function syncOverallArrow() {
  const innerDivs = document.querySelectorAll("[id^='inner-div-']");
  const anyOpen   = Array.from(innerDivs).some(div => div.style.display !== "none");

  const icon = anyOpen ? OPEN_ICON : CLOSED_ICON;

  const arrowMain   = document.getElementById("toggle-all");
  const arrowBottom = document.getElementById("toggle-all-btm");

  if (arrowMain)   arrowMain.innerHTML   = icon;
  if (arrowBottom) arrowBottom.innerHTML = icon;

  // keep global flag consistent with real state
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

  // Only update main/bottom buttons if expanding
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

  // Update inner divs
  innerDivs.forEach(div => div.style.display = displayStyle);

  // Update all row arrow buttons
  arrowButtons.forEach(btn => btn.innerHTML = icon);

  // Update header and bottom toggle buttons
  if (arrowButtonMain) arrowButtonMain.innerHTML = icon;
  if (arrowButtonBtm) arrowButtonBtm.innerHTML = icon;

  // Flip state
  isAllExpanded = expand;
}


function downloadReportImage() {
  const target = document.querySelector('.container');

  html2canvas(target, {
    backgroundColor: null, // keep transparency
    scale: 2               // higher resolution
  }).then(canvas => {

    // build timestamp:  yyyyMMdd_HHmmss
    const now   = new Date();
    const ts    = now.toISOString().replace(/[-:T]/g, '').slice(0, 15); // yyyyMMddHHmmss
    const fileName = `CucumberTestSummary_${ts}.png`;

    // trigger download
    canvas.toBlob(blob => {
      const url = URL.createObjectURL(blob);
      const a   = document.createElement('a');
      a.href = url;
      a.download = fileName;
      a.click();
      URL.revokeObjectURL(url);         // free memory
    }, 'image/png');
  });
}
const pass   = $overallPassCount;
const fail   = $overallFailCount;
const skipped = $overallSkipCount;
const total  = $overallCount;

const percentage = ((pass / total) * 100).toFixed(0) + '%';

/* ───────── Centre‑text plugin ───────── */
const centerTextPlugin = {
  id: 'centerText',
  afterDraw(chart) {                           // draw **after** the doughnut appears
    const { ctx, width, height } = chart;

    const centerX = width / 2;
    const centerY = height / 2;
    const mainSize = Math.round(height / 8);   // dynamic font sizes
    const subSize  = Math.round(height / 18);

    ctx.save();
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';

    // main % line
    ctx.font = `bold ${mainSize}px sans-serif`;
    ctx.fillStyle = '#000';
    ctx.fillText(percentage, centerX + 5, centerY - subSize);  // ⬅ visually centered

    // x / y Passed line
    ctx.font = `${subSize}px sans-serif`;
    ctx.fillStyle = '#555';
    ctx.fillText(`${pass}/${total} Passed`, centerX + 3, centerY + subSize * 1.2);

    ctx.restore();
  }
};

/* ───────── Chart config ───────── */
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
    cutout: '55%',              // ring thickness
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
  plugins: [centerTextPlugin]    // register the custom plugin per‑chart
};

/* ───────── Render ───────── */
const myChart = new Chart(document.getElementById('myPieChart'), config);

function filterScenarios() {
  /* Make sure all features are open first */
  if (!isAllExpanded) {        // global flag you already maintain
    toggleAll();               // this sets isAllExpanded = true
  }

  const selected = document.getElementById("scenarioFilter").value;
  const tables   = document.querySelectorAll(".inner-div table.dataTable");

  tables.forEach(table => {
    let visible = 0;
    const rows = table.querySelectorAll("tr:not(.data-heading)");

    rows.forEach(row => {
      const dot = row.querySelector(".circle-tc");
      if (!dot) return;

      /* get status colour class */
      const status = dot.classList.contains("green") ? "green" :
                     dot.classList.contains("red")   ? "red"   :
                     dot.classList.contains("cyan")  ? "cyan"  : "";

      const show = (selected === "all" || status === selected);
      row.style.display = show ? "" : "none";
      if (show) visible++;
    });

    /* Optional: hide the whole feature block if no scenarios match */
    const featureRow = table.closest(".row");
    featureRow.style.display = (visible > 0) ? "flex" : "none";
  });
}

function resetFilter() {
  // 1. Reset checkboxes
  document.querySelectorAll(".status-filter").forEach(cb => cb.checked = true);

  // 2. Apply filters (expands everything)
  filterScenarios();

  // 3. Collapse all (if currently expanded)
  if (isAllExpanded) {
    toggleAll(); // This will also reset the arrow icons and set isAllExpanded = false
  }
}


document.querySelectorAll(".status-filter").forEach(cb =>
  cb.addEventListener("change", filterScenarios)
);

function filterScenarios() {
  if (!isAllExpanded) toggleAll(); // expand if not already

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
