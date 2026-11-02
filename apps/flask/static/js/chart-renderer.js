(function () {
    // Minimum horizontal distance between two points. When the data does not fit,
    // the chart shows a window of points and can be dragged left and right.
    const MIN_POINT_SPACING = 8;
    const DRAG_THRESHOLD = 6;
    // A point after missing data is placed this many steps away from the previous one.
    const GAP_STEPS = 3;

    function renderPriceChart(chartCanvas) {
        let labels = JSON.parse(chartCanvas.dataset.labels || "[]");
        let values = JSON.parse(chartCanvas.dataset.values || "[]");
        let profits = JSON.parse(chartCanvas.dataset.profits || "[]");
        let profitPercents = JSON.parse(chartCanvas.dataset.profitPercents || "[]");
        let gaps = JSON.parse(chartCanvas.dataset.gaps || "[]");
        let selectedInterval = chartCanvas.dataset.interval || "daily";
        const chartInfo = chartCanvas.parentElement.querySelector(".chart-info");
        const selectLatestByDefault = chartCanvas.dataset.selectLatest === "true";
        const context = chartCanvas.getContext("2d");
        let minValue = 0;
        let maxValue = 0;
        let valueRange = 1;
        let width = chartCanvas.width;
        let height = chartCanvas.height;
        let leftPadding = 72;
        let rightPadding = 18;
        let topPadding = 48;
        let bottomPadding = 24;
        let fontSize = 12;
        let xStep = 1;
        let selectedIndex = null;
        // Position of each point on the x axis, measured in point steps.
        let positions = [];
        let totalSpan = 0;
        // Position of the left edge of the plot (may be fractional while dragging).
        let viewStart = 0;
        let visibleCount = 1;
        let drag = null;

        if (chartCanvas.priceChart) {
            return chartCanvas.priceChart;
        }

        function refreshPositions() {
            positions = [];

            values.forEach((value, index) => {
                if (index === 0) {
                    positions.push(0);
                } else {
                    positions.push(positions[index - 1] + (gaps[index] ? GAP_STEPS : 1));
                }
            });

            totalSpan = positions.length ? positions[positions.length - 1] + 1 : 0;
        }

        // Index of the first point whose position is >= the given position.
        function findIndexAtOrAfter(position) {
            let low = 0;
            let high = positions.length;

            while (low < high) {
                const middle = Math.floor((low + high) / 2);

                if (positions[middle] < position) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }

            return low;
        }

        function findNearestIndex(position) {
            const after = Math.min(findIndexAtOrAfter(position), positions.length - 1);
            const before = Math.max(after - 1, 0);

            return Math.abs(positions[before] - position) <= Math.abs(positions[after] - position)
                ? before
                : after;
        }

        function getMaxViewStart() {
            return Math.max(totalSpan - visibleCount, 0);
        }

        function clampViewStart(nextViewStart) {
            return Math.max(0, Math.min(getMaxViewStart(), nextViewStart));
        }

        function getVisibleIndexRange() {
            const viewEnd = viewStart + visibleCount - 1;
            let first = Math.min(findIndexAtOrAfter(viewStart - 0.5), values.length - 1);
            let last = findIndexAtOrAfter(viewEnd + 0.5) - 1;

            // Only a gap is visible: show the points on both sides of it.
            if (last < first) {
                first = Math.max(last, 0);
                last = Math.min(first + 1, values.length - 1);
            }

            return { first: Math.max(first, 0), last: Math.max(last, 0) };
        }

        function refreshValueRange() {
            if (!values.length) {
                minValue = 0;
                maxValue = 0;
                valueRange = 1;
                return;
            }

            const { first, last } = getVisibleIndexRange();
            const visibleValues = values.slice(first, last + 1);

            minValue = Math.min(...visibleValues);
            maxValue = Math.max(...visibleValues);
            valueRange = maxValue - minValue || 1;
        }

        function resizeChart() {
            const pixelRatio = window.devicePixelRatio || 1;
            const isMobile = window.matchMedia("(max-width: 700px)").matches;
            const panelStyle = window.getComputedStyle(chartCanvas.parentElement);
            const panelHorizontalPadding = parseFloat(panelStyle.paddingLeft) + parseFloat(panelStyle.paddingRight);
            const chartWidth = chartCanvas.parentElement.clientWidth - panelHorizontalPadding - 2;
            const isAtEnd = viewStart >= getMaxViewStart();

            width = Math.max(chartWidth, 280);
            height = isMobile ? 280 : 320;
            topPadding = isMobile ? 42 : 40;
            bottomPadding = 18;
            fontSize = isMobile ? 17 : 15;

            chartCanvas.style.width = `${width}px`;
            chartCanvas.style.height = `${height}px`;
            chartCanvas.width = Math.floor(width * pixelRatio);
            chartCanvas.height = Math.floor(height * pixelRatio);

            context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
            context.font = `600 ${fontSize}px Arial`;

            // Use the full data range for the label width, so the plot does not
            // shift sideways while dragging.
            const allMinValue = values.reduce((result, value) => Math.min(result, value), Infinity);
            const allMaxValue = values.reduce((result, value) => Math.max(result, value), -Infinity);
            const widestValueLabel = values.length
                ? Math.max(
                    context.measureText(allMaxValue.toFixed(2)).width,
                    context.measureText(allMinValue.toFixed(2)).width
                )
                : 0;
            leftPadding = Math.ceil(widestValueLabel) + 20;
            rightPadding = isMobile ? 14 : 18;

            const plotWidth = Math.max(width - leftPadding - rightPadding, 1);
            visibleCount = Math.max(
                Math.min(totalSpan, Math.floor(plotWidth / MIN_POINT_SPACING) + 1),
                1
            );
            xStep = plotWidth / Math.max(visibleCount - 1, 1);
            viewStart = isAtEnd ? getMaxViewStart() : clampViewStart(viewStart);
            refreshValueRange();
        }

        function getPoint(index) {
            const x = leftPadding + xStep * (positions[index] - viewStart);
            const value = values[index];
            const chartHeight = height - topPadding - bottomPadding;
            const y = height - bottomPadding - ((value - minValue) / valueRange) * chartHeight;

            return { x, y, value };
        }

        function updateChartInfo() {
            if (!chartInfo || selectedIndex === null || !values.length) {
                return;
            }

            const label = ["recorded", "hourly"].includes(selectedInterval) ? "Time" : "Period";
            const valueLabel = chartCanvas.dataset.valueLabel
                || (selectedInterval === "recorded" ? "Price" : "Average price");

            let text = `${label}: ${labels[selectedIndex]} | ${valueLabel}: ${values[selectedIndex].toFixed(2)}`;

            const profit = profits[selectedIndex];
            if (profit !== undefined && profit !== null) {
                text += ` | Profit: ${Number(profit).toFixed(2)}`;

                const profitPercent = profitPercents[selectedIndex];
                if (profitPercent !== undefined && profitPercent !== null) {
                    text += ` (${Number(profitPercent).toFixed(2)}%)`;
                }
            }

            chartInfo.textContent = text;
        }

        function drawScrollIndicator() {
            if (totalSpan <= visibleCount) {
                return;
            }

            const trackWidth = width - leftPadding - rightPadding;
            const thumbWidth = Math.max((visibleCount / totalSpan) * trackWidth, 24);
            const thumbX = leftPadding + (viewStart / getMaxViewStart()) * (trackWidth - thumbWidth);
            const y = height - 4;

            context.lineCap = "round";
            context.lineWidth = 4;
            context.strokeStyle = "#e4e7eb";
            context.beginPath();
            context.moveTo(leftPadding, y);
            context.lineTo(leftPadding + trackWidth, y);
            context.stroke();

            context.strokeStyle = "#9aa5b1";
            context.beginPath();
            context.moveTo(thumbX, y);
            context.lineTo(thumbX + thumbWidth, y);
            context.stroke();
            context.lineCap = "butt";
        }

        function drawChart(selectedIndex = null) {
            context.clearRect(0, 0, width, height);
            context.font = `600 ${fontSize}px Arial`;

            if (!values.length) {
                context.fillStyle = "#52606d";
                context.fillText("No price data available.", leftPadding, topPadding);
                return;
            }

            context.strokeStyle = "#d9e2ec";
            context.lineWidth = 1;

            for (let i = 0; i <= 4; i += 1) {
                const y = topPadding + ((height - topPadding - bottomPadding) / 4) * i;
                context.beginPath();
                context.moveTo(leftPadding, y);
                context.lineTo(width - rightPadding, y);
                context.stroke();
            }

            // Draw one extra point on each side so lines leave the plot smoothly,
            // and clip everything to the plot area.
            const visibleRange = getVisibleIndexRange();
            const first = Math.max(visibleRange.first - 1, 0);
            const last = Math.min(visibleRange.last + 1, values.length - 1);

            context.save();
            context.beginPath();
            context.rect(leftPadding - 6, 0, width - leftPadding - rightPadding + 12, height - bottomPadding + 8);
            context.clip();

            context.strokeStyle = "#0b5cad";
            context.lineWidth = 3;
            context.beginPath();

            for (let index = first; index <= last; index += 1) {
                const point = getPoint(index);

                // Start a new line segment after missing data instead of joining across the gap.
                if (index === first || gaps[index]) {
                    context.moveTo(point.x, point.y);
                } else {
                    context.lineTo(point.x, point.y);
                }
            }

            context.stroke();

            context.fillStyle = "#0b5cad";
            for (let index = first; index <= last; index += 1) {
                const point = getPoint(index);

                context.beginPath();
                context.arc(point.x, point.y, 4, 0, Math.PI * 2);
                context.fill();
            }

            if (selectedIndex !== null && selectedIndex >= first && selectedIndex <= last) {
                const point = getPoint(selectedIndex);

                context.strokeStyle = "#b42318";
                context.lineWidth = 2;
                context.beginPath();
                context.moveTo(point.x, topPadding);
                context.lineTo(point.x, height - bottomPadding);
                context.stroke();

                context.fillStyle = "#b42318";
                context.beginPath();
                context.arc(point.x, point.y, 6, 0, Math.PI * 2);
                context.fill();
            }

            context.restore();

            context.fillStyle = "#52606d";
            context.textAlign = "right";
            context.fillText(maxValue.toFixed(2), leftPadding - 10, topPadding + 5);
            context.fillText(minValue.toFixed(2), leftPadding - 10, height - bottomPadding + 5);
            context.textAlign = "left";

            drawScrollIndicator();
        }

        function scrollTo(nextViewStart) {
            const clampedViewStart = clampViewStart(nextViewStart);

            if (clampedViewStart === viewStart) {
                return;
            }

            viewStart = clampedViewStart;
            refreshValueRange();
            drawChart(selectedIndex);
        }

        function selectAt(clientX) {
            const rect = chartCanvas.getBoundingClientRect();
            const clickX = clientX - rect.left;
            const nearestIndex = findNearestIndex((clickX - leftPadding) / xStep + viewStart);
            const { first, last } = getVisibleIndexRange();
            selectedIndex = Math.max(first, Math.min(last, nearestIndex));

            drawChart(selectedIndex);
            updateChartInfo();
        }

        refreshPositions();
        resizeChart();
        viewStart = getMaxViewStart();
        refreshValueRange();
        selectedIndex = selectLatestByDefault && values.length ? values.length - 1 : null;
        drawChart(selectedIndex);
        updateChartInfo();

        // Vertical swipes keep scrolling the page; horizontal swipes move the chart.
        chartCanvas.style.touchAction = "pan-y";
        chartCanvas.style.cursor = "grab";

        chartCanvas.addEventListener("pointerdown", (event) => {
            if (!values.length || event.button > 0) {
                return;
            }

            drag = {
                pointerId: event.pointerId,
                startX: event.clientX,
                startViewStart: viewStart,
                moved: false,
            };
        });

        chartCanvas.addEventListener("pointermove", (event) => {
            if (!drag || drag.pointerId !== event.pointerId) {
                return;
            }

            const distance = event.clientX - drag.startX;

            if (!drag.moved && Math.abs(distance) < DRAG_THRESHOLD) {
                return;
            }

            if (!drag.moved) {
                drag.moved = true;
                chartCanvas.setPointerCapture(event.pointerId);
                chartCanvas.style.cursor = "grabbing";
            }

            scrollTo(drag.startViewStart - distance / xStep);
        });

        chartCanvas.addEventListener("pointerup", (event) => {
            if (!drag || drag.pointerId !== event.pointerId) {
                return;
            }

            if (!drag.moved) {
                selectAt(event.clientX);
            }

            drag = null;
            chartCanvas.style.cursor = "grab";
        });

        chartCanvas.addEventListener("pointercancel", () => {
            drag = null;
            chartCanvas.style.cursor = "grab";
        });

        // Horizontal trackpad scrolling (or Shift + mouse wheel) moves the chart.
        chartCanvas.addEventListener("wheel", (event) => {
            const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY)
                ? event.deltaX
                : (event.shiftKey ? event.deltaY : 0);

            if (!delta || totalSpan <= visibleCount) {
                return;
            }

            event.preventDefault();
            scrollTo(viewStart + delta / xStep);
        }, { passive: false });

        window.addEventListener("resize", () => {
            resizeChart();
            drawChart(selectedIndex);
        });

        chartCanvas.priceChart = {
            redraw() {
                resizeChart();
                drawChart(selectedIndex);
            },
            update(nextLabels, nextValues, nextInterval, nextProfits, nextProfitPercents, nextGaps) {
                labels = nextLabels;
                values = nextValues;
                selectedInterval = nextInterval;
                selectedIndex = selectLatestByDefault && values.length ? values.length - 1 : null;
                chartCanvas.dataset.labels = JSON.stringify(labels);
                chartCanvas.dataset.values = JSON.stringify(values);
                chartCanvas.dataset.interval = selectedInterval;
                if (nextProfits !== undefined) {
                    profits = nextProfits;
                    chartCanvas.dataset.profits = JSON.stringify(profits);
                }
                if (nextProfitPercents !== undefined) {
                    profitPercents = nextProfitPercents;
                    chartCanvas.dataset.profitPercents = JSON.stringify(profitPercents);
                }
                gaps = nextGaps || [];
                chartCanvas.dataset.gaps = JSON.stringify(gaps);
                refreshPositions();
                resizeChart();
                viewStart = getMaxViewStart();
                refreshValueRange();
                drawChart(selectedIndex);

                if (chartInfo) {
                    if (selectedIndex !== null) {
                        updateChartInfo();
                    } else {
                        chartInfo.textContent = values.length
                            ? "Click on the chart to inspect price."
                            : "No price data available for this item.";
                    }
                }
            },
        };

        return chartCanvas.priceChart;
    }

    document.querySelectorAll(".price-chart").forEach(renderPriceChart);
    window.PriceCharts = {
        render: renderPriceChart,
        redrawAll() {
            document.querySelectorAll(".price-chart").forEach((chartCanvas) => {
                renderPriceChart(chartCanvas).redraw();
            });
        },
        update(chartCanvas, labels, values, interval, profits, profitPercents, gaps) {
            renderPriceChart(chartCanvas).update(labels, values, interval, profits, profitPercents, gaps);
        },
    };
}());
