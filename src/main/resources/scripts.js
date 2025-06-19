var isAllExpanded = false; // Keeps track of the toggle state
function toggleInnerDiv(rowId) {
    var innerDiv = document.getElementById("inner-div-" + rowId);
    var arrowButton = document.getElementById("arrow-button-" + rowId);
    var arrowButtonMain = document.getElementById("toggle-all");
    var arrowButtonBtm = document.getElementById("toggle-all-btm");
    var openSymbol = '<i class="fas fa-angle-up  fa-lg"></i>'; // Eject symbol
    var closedSymbol = '<i class="fas fa-angle-right  fa-lg"></i>'; // Rightwards arrow
    if (innerDiv.style.display === "none") {
        innerDiv.style.display = "block";
        arrowButton.innerHTML = openSymbol;
        arrowButtonMain.innerHTML = openSymbol;
        arrowButtonBtm.innerHTML = openSymbol;
        isAllExpanded = true;
    } else {
        innerDiv.style.display = "none";
        arrowButton.innerHTML = closedSymbol;
    }
}

function toggleAll() {
    // Get all inner div elements and arrow buttons
    var innerDivs = document.querySelectorAll("[id^='inner-div-']");
    var arrowButtons = document.querySelectorAll("[id^='arrow-button-']");
    var arrowButtonMain = document.getElementById("toggle-all");
    var arrowButtonBtm = document.getElementById("toggle-all-btm");
    var openSymbol = '<i class="fas fa-angle-up  fa-lg"></i>'; // Eject symbol
    var closedSymbol = '<i class="fas fa-angle-right  fa-lg"></i>'; // Rightwards arrow

    if (isAllExpanded) {
        // Collapse all
        innerDivs.forEach(function (innerDiv) {
            innerDiv.style.display = "none";
        });
        arrowButtons.forEach(function (arrowButton) {
            arrowButton.innerHTML = closedSymbol;
        });
        arrowButtonMain.innerHTML = closedSymbol;
        arrowButtonBtm.innerHTML = closedSymbol;
        isAllExpanded = false;
    } else {
        // Expand all
        innerDivs.forEach(function (innerDiv) {
            innerDiv.style.display = "block";
        });
        arrowButtons.forEach(function (arrowButton) {
            arrowButton.innerHTML = openSymbol;
        });
        arrowButtonMain.innerHTML = openSymbol;
        arrowButtonBtm.innerHTML = openSymbol;
        isAllExpanded = true;
    }
}

function downloadReportImage() {
              const target = document.querySelector('.container');
              html2canvas(target, {
                <!--backgroundColor: null,  // keep transparency if needed-->
                scale: 2                // increase for higher resolution
              }).then(canvas => {
                const link = document.createElement('a');
                link.download = 'cucumber-test-summary.png';
                link.href = canvas.toDataURL('image/png');
                link.click();
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
  /* 1️⃣  Make sure all features are open first */
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
  // 1. set dropdown back to “All”
  document.getElementById("scenarioFilter").value = "all";

  // 2. re‑apply filter (this will expand all and show everything)
  filterScenarios();

  // 3. OPTIONAL: collapse everything afterwards
  //    comment out these two lines if you prefer everything to remain open
  if (isAllExpanded) {
    toggleAll();          // will collapse and flip icons
  }
}

document.querySelectorAll(".status-filter").forEach(cb =>
  cb.addEventListener("change", filterScenarios)
);

function resetFilter() {
  document.querySelectorAll(".status-filter").forEach(cb => cb.checked = true);
  filterScenarios();
}

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
