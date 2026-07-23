+++
adr = "0016"

[[covers]]
id = "bootstrap-ui"
version = "^5.3.8"
manifest = "frontend/package.json"
+++

# ADR-0016: UI Styling — Bootstrap 5, Bootstrap Icons, and Popper.js

## Status
Accepted

## Context
Inventra is a multi-tenant inventory and order management SaaS with an Angular 21 frontend. The application requires a comprehensive UI component library that provides:

- **Responsive layout primitives:** Grid system, flexbox utilities, and responsive breakpoints for desktop, tablet, and mobile views.
- **Pre-built interactive components:** Modals, dropdowns, tooltips, popovers, navigation bars, forms, and buttons that work consistently across browsers without custom JavaScript.
- **Accessible markup patterns:** ARIA attributes, keyboard navigation, and focus management built into components by default.
- **Icon library:** A consistent set of scalable vector icons for actions, status indicators, and navigation elements.
- **Theming and customization:** CSS variables and Sass variables that allow Inventra to apply brand colors and spacing without forking the library.

The frontend declares three npm packages in `frontend/package.json`:

- `bootstrap` (version `^5.3.8`) — the core CSS framework and JavaScript component library.
- `bootstrap-icons` (version `^1.13.1`) — the official icon font and SVG library.
- `@popperjs/core` (version `^2.11.8`) — the positioning engine used by Bootstrap's dropdown, tooltip, and popover components.

Bootstrap 5.3.x is the current stable release series. It is the first Bootstrap generation to drop jQuery as a dependency, relying instead on vanilla JavaScript and the Popper.js positioning library. This aligns with Angular's component model, which does not use jQuery and benefits from Bootstrap's lighter runtime footprint.

Bootstrap Icons 1.13.x provides over 2,000 icons as both web fonts and individual SVG files. The icon set is designed to match Bootstrap's visual style and is maintained by the same team, ensuring consistency with the component library.

Popper.js 2.11.x is a peer dependency of Bootstrap 5. It is a standalone positioning library that calculates optimal placement for floating elements (dropdowns, tooltips, popovers) relative to their trigger elements, handling viewport boundaries, scroll containers, and dynamic content resizing. Bootstrap's JavaScript components delegate all positioning logic to Popper, keeping Bootstrap's own codebase focused on component behavior rather than geometry calculations.

## Decision
Use `bootstrap` (version `^5.3.8`), `bootstrap-icons` (version `^1.13.1`), and `@popperjs/core` (version `^2.11.8`) as the UI styling and component foundation for the Inventra frontend.

Source manifest: `frontend/package.json`

Bootstrap's CSS is imported globally in `frontend/src/styles.css`, and its JavaScript components are imported on-demand in Angular components that require interactive behavior (e.g., modals, dropdowns). Bootstrap Icons are used as inline SVG elements via Angular's `DomSanitizer` or as CSS classes when using the web font variant. Popper.js is not imported directly by application code; it is a transitive dependency consumed by Bootstrap's JavaScript.

## Consequences

**Advantages:**

- **Rapid prototyping and consistent UI:** Bootstrap's pre-built components and utility classes allow Inventra's frontend to achieve a polished, professional appearance without writing custom CSS for every layout and interaction. The grid system and responsive utilities handle multi-device support declaratively, reducing the need for media query boilerplate.
- **Accessibility by default:** Bootstrap 5 components include ARIA attributes, keyboard navigation, and focus management out of the box. Modals trap focus, dropdowns support arrow-key navigation, and form controls have proper label associations. This reduces the accessibility testing burden for Inventra and ensures compliance with WCAG 2.1 Level AA guidelines without manual intervention.
- **No jQuery dependency:** Bootstrap 5's removal of jQuery eliminates a 30KB runtime dependency and avoids the impedance mismatch between jQuery's DOM manipulation model and Angular's change detection. Bootstrap's vanilla JavaScript components integrate cleanly with Angular's component lifecycle.
- **Large ecosystem and documentation:** Bootstrap is the most widely adopted CSS framework, with extensive documentation, community plugins, and third-party themes. Inventra developers can find solutions to common UI problems quickly, and new team members are likely to have prior Bootstrap experience.
- **Icon library consistency:** Bootstrap Icons are designed to match Bootstrap's visual language, ensuring that icons and UI components feel cohesive. The library is maintained in lockstep with Bootstrap releases, avoiding version skew between the icon set and the component library.

**Disadvantages / costs / risks:**

- **Large CSS bundle size:** Bootstrap's full CSS distribution is approximately 200KB uncompressed (60KB gzipped). Inventra does not use every Bootstrap component, but the default import includes all styles. This increases the initial page load time compared to a utility-first framework like Tailwind CSS, which tree-shakes unused styles at build time.
  - *Mitigation (planned):* Bootstrap 5 supports Sass-based customization, allowing Inventra to import only the component modules it uses. A future optimization task will replace the full `bootstrap.css` import with a custom Sass build that excludes unused components (e.g., carousel, accordion, offcanvas). This is tracked in the frontend backlog.
- **Opinionated design language:** Bootstrap's default visual style (rounded corners, blue primary color, specific spacing scale) is recognizable and may make Inventra's UI feel generic if not customized. Applying a distinct brand identity requires overriding Bootstrap's CSS variables or Sass variables.
  - *Mitigation (applied):* Inventra's `frontend/src/styles.css` overrides Bootstrap's primary color palette and spacing scale using CSS custom properties (`--bs-primary`, `--bs-body-font-family`). This customization is applied globally and does not require forking Bootstrap's source.
- **Popper.js as a mandatory peer dependency:** Bootstrap's dropdown, tooltip, and popover components will not function without Popper.js. If Inventra's frontend does not use these components, Popper.js is dead code in the bundle. However, Inventra's navigation bar uses dropdowns, and the application uses tooltips for icon buttons, so Popper.js is a necessary dependency.
- **Bootstrap Icons web font vs. SVG trade-off:** The web font variant of Bootstrap Icons is convenient (single CSS class per icon) but loads all 2,000+ icons even if only a handful are used, resulting in a ~100KB font file. The SVG variant allows tree-shaking but requires importing each icon individually in Angular components.
  - *Mitigation (applied):* Inventra uses the SVG variant of Bootstrap Icons, importing only the icons used in each component. This is enforced by the frontend linting rules, which flag unused imports.

### Alternatives considered

- **Tailwind CSS (instead of Bootstrap):** Tailwind is a utility-first CSS framework that generates styles on-demand based on class usage, resulting in smaller production bundles. It was rejected because Tailwind provides no pre-built interactive components — dropdowns, modals, and tooltips must be implemented from scratch or sourced from third-party libraries. Bootstrap's component library reduces Inventra's frontend development time and ensures consistent behavior across components. Tailwind remains a future migration candidate if bundle size becomes a critical constraint.
- **Material Design (Angular Material) (instead of Bootstrap):** Angular Material is the official Material Design component library for Angular, with deep integration into Angular's ecosystem (CDK, animations, theming). It was rejected because Material Design's visual language is strongly associated with Google products, and Inventra's design requirements favor a more neutral, business-application aesthetic. Material's theming system is also more complex than Bootstrap's CSS variable overrides, increasing the customization effort.
- **PrimeNG (instead of Bootstrap):** PrimeNG is a comprehensive Angular component library with a large set of data-oriented components (data tables, tree views, charts). It was rejected because PrimeNG's default theme is visually dated compared to Bootstrap 5, and its documentation is less extensive. PrimeNG's data table component is more feature-rich than Bootstrap's table utilities, but Inventra's table requirements are met by Bootstrap's responsive table classes combined with Angular's `*ngFor` and sorting logic.
- **Font Awesome (instead of Bootstrap Icons):** Font Awesome is the most popular icon library, with over 10,000 icons in its Pro tier. It was rejected because Bootstrap Icons are sufficient for Inventra's needs (the application uses fewer than 50 unique icons), and Bootstrap Icons are maintained by the same team as Bootstrap, ensuring visual consistency. Font Awesome's free tier is comparable in size to Bootstrap Icons, so there is no bundle size advantage.

### References

- <https://getbootstrap.com/>
- <https://icons.getbootstrap.com/>
- <https://popper.js.org/>

