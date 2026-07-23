import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      provider: 'v8',
      reportsDirectory: 'coverage',
      exclude: [
        'node_modules/**',
        'dist/**',
        'coverage/**',
        '**/*.spec.ts',
        '**/*.test.ts',
        'src/testing/**',
        'src/main.ts',
        'src/app/app.config.ts',
        'src/app/app.routes.ts',
      ],
      thresholds: {
        statements: 70,
        branches: 75,
        functions: 60,
        lines: 75,
      },
    },
  },
});
