import { Injectable, inject } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd } from '@angular/router';
import { Observable } from 'rxjs';
import { filter, map, startWith } from 'rxjs/operators';

export interface BreadcrumbSegment {
  label: string;
  path: string | null; // null for the last (current) segment
}

@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);

  readonly breadcrumbs$: Observable<BreadcrumbSegment[]> = this.router.events.pipe(
    filter(e => e instanceof NavigationEnd),
    startWith(null),
    map(() => this.buildBreadcrumbs(this.activatedRoute.root))
  );

  private buildBreadcrumbs(
    route: ActivatedRoute,
    path = '',
    segments: BreadcrumbSegment[] = []
  ): BreadcrumbSegment[] {
    const children = route.children;
    if (children.length === 0) return segments;

    for (const child of children) {
      // During lazy-route resolution or initial subscription, a child's
      // snapshot may not be populated yet — skip those defensively.
      const snapshot = child.snapshot;
      if (!snapshot) continue;

      const routeURL = (snapshot.url ?? []).map(s => s.path).join('/');
      const newPath = routeURL ? `${path}/${routeURL}` : path;
      const breadcrumb = snapshot.data?.['breadcrumb'];
      if (breadcrumb) {
        // Skip duplicates: lazy-route empty-path children inherit the parent's
        // `data: { breadcrumb }`, which would otherwise produce "Orders / Orders".
        const last = segments[segments.length - 1];
        if (!last || last.label !== breadcrumb) {
          segments.push({ label: breadcrumb, path: newPath || '/' });
        }
      }
      this.buildBreadcrumbs(child, newPath, segments);
    }

    if (segments.length > 0) {
      segments[segments.length - 1] = { ...segments[segments.length - 1], path: null };
    }

    return segments;
  }
}
