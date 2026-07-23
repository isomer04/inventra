// @ts-check
//
// Flat-config ESLint setup for Angular 21.
//
// Run:
//   npm run lint
//   npm run lint:fix
//
// Initial install (already done if angular-eslint is in package.json):
//   npm install --save-dev angular-eslint @typescript-eslint/eslint-plugin \
//                          @typescript-eslint/parser eslint typescript-eslint

const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = tseslint.config(
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // Component selector prefix
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],

      // Allow unused vars when prefixed with _
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // no-explicit-any set to error — untyped any silently
      // propagates through auth and API handling code. Use proper
      // types or 'unknown' before merging if new any errors surface.
      '@typescript-eslint/no-explicit-any': 'error',

      // Allow empty constructors used for DI
      '@typescript-eslint/no-empty-function': ['warn', { allow: ['constructors'] }],
    },
  },

  {
    files: ['**/*.spec.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },

  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      // Be explicit about deps that touch user input
      '@angular-eslint/template/no-inline-styles': 'warn',
    },
  },
);
