var isAllExpanded = false; // Keeps track of the toggle state
function toggleInnerDiv(rowId) {
    var innerDiv = document.getElementById("inner-div-" + rowId);
    var arrowButton = document.getElementById("arrow-button-" + rowId);
    var arrowButtonMain = document.getElementById("toggle-all");
    var arrowButtonBtm = document.getElementById("toggle-all-btm");
    var openSymbol = '\u23CF'; // Eject symbol
    var closedSymbol = '\u27A4'; // Rightwards arrow
    if (innerDiv.style.display === "none") {
        innerDiv.style.display = "block";
        arrowButton.innerText = openSymbol;
        arrowButtonMain.innerText = openSymbol;
        arrowButtonBtm.innerText = openSymbol;
        isAllExpanded = true;
    } else {
        innerDiv.style.display = "none";
        arrowButton.innerText = closedSymbol;
    }
}

function toggleAll() {
    // Get all inner div elements and arrow buttons
    var innerDivs = document.querySelectorAll("[id^='inner-div-']");
    var arrowButtons = document.querySelectorAll("[id^='arrow-button-']");
    var arrowButtonMain = document.getElementById("toggle-all");
    var arrowButtonBtm = document.getElementById("toggle-all-btm");
    var openSymbol = '\u23CF'; // Eject symbol
    var closedSymbol = '\u27A4'; // Rightwards arrow

    if (isAllExpanded) {
        // Collapse all
        innerDivs.forEach(function (innerDiv) {
            innerDiv.style.display = "none";
        });
        arrowButtons.forEach(function (arrowButton) {
            arrowButton.innerText = closedSymbol;
        });
        arrowButtonMain.innerText = closedSymbol;
        arrowButtonBtm.innerText = closedSymbol;
        isAllExpanded = false;
    } else {
        // Expand all
        innerDivs.forEach(function (innerDiv) {
            innerDiv.style.display = "block";
        });
        arrowButtons.forEach(function (arrowButton) {
            arrowButton.innerText = openSymbol;
        });
        arrowButtonMain.innerText = openSymbol;
        arrowButtonBtm.innerText = openSymbol;
        isAllExpanded = true;
    }
}

const data = {
    labels: ['Pass', 'Fail', 'Skipped'],
    datasets: [{
        data: [$overallPassCount, $overallFailCount, ($overallCount - $overallPassCount - $overallFailCount)], // Replace these values with actual pass, fail, and skipped counts
        backgroundColor: ["#00B000", "#FF3030", "#88AAFF", "#F5F28F", "#F5B975"],
        hoverOffset: 5
    }]
};

// Configuration object
const config = {
    type: 'doughnut',
    data: data,
    options: {
        plugins: {
            legend: {
                display: false // Hides the static labels
            },
            tooltip: {
                callbacks: {
                    // This function sets the tooltip text on hover
                    label: function (context) {
                        const label = context.label || '';
                        const value = context.raw;
                        const total = context.dataset.data.reduce((sum, val) => sum + val, 0);
                        const percentage = ((value / total) * 100).toFixed(2); // Calculate percentage
                        return `${label}: ${value} (${percentage}%)`;
                    },
                    title: function () {
                        return ''; // Remove the default title
                    }
                },
                bodyFont: {
                    size: 14
                },
                padding: 10
            }
        },
        responsive: true,
        maintainAspectRatio: false,
        cutout: '50%'
    }
};

// Render the chart
const myPieChart = new Chart(
    document.getElementById('myPieChart'),
    config
);