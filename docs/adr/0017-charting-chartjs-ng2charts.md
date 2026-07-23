+++
adr = "0017"

[[covers]]
id = "charting"
version = "^4.5.1"
manifest = "frontend/package.json"
+++

# ADR-0017: Charting via Chart.js and ng2-charts

## Status
Accepted

## Context
Inventra's Angular frontend displays inventory metrics, order trends, and stock-level visualisations in the dashboard and analytics views. These visualisations include line charts for order volume over time, bar charts for product category distribution, and doughnut charts for stock status summaries. The charting solution must integrate cleanly with Angular's component lifecycle, support responsive layouts for mobile and desktop views, and provide accessible chart rendering that works with screen readers.

The project needs a charting library that can:
- Render common chart types (line, bar, doughnut, pie) with smooth animations and responsive sizing.
- Integrate with Angular's change detection and component lifecycle without manual DOM manipulation.
- Support accessibility features (ARIA labels, keyboard navigation) for WCAG 2.1 AA compliance.
- Provide a declarative API that fits Angular's template-driven approach.
- Maintain a small bundle footprint to keep the frontend build size under control.

The two packages declared in `frontend/package.json` are:
- `chart.js` at version `^4.5.1` — the core charting library that renders charts to HTML5 canvas.
- `ng2-charts` at version `^10.0.0` — the Angular wrapper that exposes Chart.js as Angular components and directives.

## Decision
Use `chart.js` at version `^4.5.1` and `ng2-charts` at version `^10.0.0`, as declared in `frontend/package.json`.

Source manifest: `frontend/package.json`

Chart.js is the underlying rendering engine. It is a standalone, framework-agnostic library that draws charts to a `<canvas>` element using the Canvas 2D API. ng2-charts wraps Chart.js in Angular components (`BaseChartDirective`, `NgChartsModule`) that handle the Angular-specific integration: binding chart data and options to component inputs, triggering chart updates on Angular change detection cycles, and cleaning up chart instances when components are destroyed.

Dashboard components import `NgChartsModule` and use the `<canvas baseChart>` directive to declare charts in templates. Chart data, labels, and options are bound as Angular inputs, and ng2-charts automatically calls Chart.js update methods when those inputs change. This keeps chart rendering declarative and avoids the imperative `new Chart()` calls that would otherwise be required in component lifecycle hooks.

## Consequences

**Advantages:**
- Chart.js is the most widely adopted JavaScript charting library, with a large ecosystem of plugins, extensive documentation, and active maintenance. This reduces the risk of encountering unsupported edge cases or abandoned dependencies.
- ng2-charts handles the Angular integration boilerplate (lifecycle hooks, change detection, cleanup) so that dashboard components can treat charts as declarative template elements. This keeps component code focused on data transformation rather than chart lifecycle management.
- Chart.js renders to `<canvas>`, which is hardware-accelerated in modern browsers and performs well even with large datasets (hundreds of data points). Inventra's order-trend charts can display a full year of daily data without frame drops.
- Chart.js 4.x includes built-in accessibility features: it generates an off-screen table of chart data for screen readers and supports keyboard navigation for interactive tooltips. This satisfies Inventra's WCAG 2.1 AA requirement without additional plugins.
- The combined bundle size of Chart.js 4.5.1 and ng2-charts 10.0.0 is approximately 200 KB minified (60 KB gzipped), which is acceptable for Inventra's frontend bundle budget.

**Disadvantages / costs / risks:**
- Chart.js uses `<canvas>` rather than SVG, which means charts are raster-based and do not scale infinitely without pixelation. For print or high-DPI export use cases, SVG-based libraries (e.g., D3.js, Highcharts) would produce sharper output. Inventra's charts are dashboard-only and not exported, so this is not a present constraint.
- ng2-charts is a thin wrapper maintained by a separate team from Chart.js. Major Chart.js releases sometimes require waiting for ng2-charts to catch up with API changes. The current ng2-charts 10.x line is compatible with Chart.js 4.x, but a future Chart.js 5.x release would require an ng2-charts upgrade before Inventra could adopt it.
- Chart.js does not support real-time streaming data out of the box. Inventra's dashboard charts are updated on user navigation or periodic polling, not via WebSocket streams, so this is not a current limitation. If real-time charting becomes a requirement, a plugin (e.g., `chartjs-plugin-streaming`) or a library swap would be needed.

**Mitigations:**
- The ng2-charts version is pinned to `^10.0.0`, which locks to the 10.x line and avoids accidental breaking changes from a major version bump. Chart.js is pinned to `^4.5.1`, which allows patch and minor updates within the 4.x line while blocking the 5.x upgrade until ng2-charts supports it.
- Chart.js plugins are loaded on-demand via Angular's lazy-loading mechanism, so unused chart types (e.g., radar, polar area) do not bloat the initial bundle.

### Alternatives considered
- **Highcharts** (`highcharts`, `highcharts-angular`) — a feature-rich commercial charting library with SVG rendering and extensive chart types. Rejected because Highcharts requires a commercial license for SaaS use, and Inventra's budget does not justify the licensing cost for the chart types currently needed. Chart.js provides the required functionality under the MIT license.
- **D3.js** (`d3`) — a low-level SVG manipulation library that offers maximum flexibility for custom visualisations. Rejected because D3 requires significant custom code to build even basic chart types (line, bar, doughnut), and Inventra's dashboard does not need bespoke visualisations. Chart.js provides the standard chart types out of the box with far less code.
- **ngx-charts** (`@swimlane/ngx-charts`) — an Angular-native charting library built on D3.js. Rejected because ngx-charts is less actively maintained than Chart.js (fewer releases, slower issue resolution), and its API is more opinionated and less flexible for customising chart appearance. Chart.js has a larger community and more third-party plugins.

### References
- <https://www.chartjs.org/>
- <https://valor-software.com/ng2-charts/>
- <https://github.com/chartjs/Chart.js>
- <https://github.com/valor-software/ng2-charts>

