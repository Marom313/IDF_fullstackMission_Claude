import js from '@eslint/js';
import boundaries from 'eslint-plugin-boundaries';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * MVVM boundaries per feature (ArchUnit-style for the frontend):
 *   view      → may import viewmodel + shared/ui, NEVER model (no fetch / no API types in views)
 *   viewmodel → may import model + shared
 *   model     → may import shared only
 */
export default tseslint.config(
  { ignores: ['dist', 'src/shared/api/generated'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { boundaries, 'react-hooks': reactHooks },
    settings: {
      'boundaries/include': ['src/**/*'],
      'boundaries/elements': [
        { type: 'model', pattern: 'src/features/*/model/**', capture: ['feature'] },
        { type: 'viewmodel', pattern: 'src/features/*/viewmodel/**', capture: ['feature'] },
        { type: 'view', pattern: 'src/features/*/view/**', capture: ['feature'] },
        { type: 'tests', pattern: 'src/features/*/__tests__/**' },
        { type: 'shared', pattern: 'src/shared/**' },
        { type: 'app', pattern: 'src/app/**' },
        { type: 'test-setup', pattern: 'src/test/**' },
      ],
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'boundaries/element-types': ['error', {
        default: 'disallow',
        rules: [
          { from: 'view', allow: ['viewmodel', 'view', 'shared'] },
          { from: 'viewmodel', allow: ['model', 'shared', 'viewmodel'] },
          { from: 'model', allow: ['shared', 'model'] },
          { from: 'shared', allow: ['shared'] },
          { from: 'app', allow: ['view', 'viewmodel', 'shared', 'app'] },
          { from: 'tests', allow: ['model', 'viewmodel', 'view', 'shared', 'test-setup'] },
          { from: 'test-setup', allow: ['shared'] },
        ],
      }],
      'no-restricted-globals': ['error', { name: 'fetch', message: 'Use shared/api/http (auth, problem details, correlation id).' }],
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    files: ['src/shared/api/http.ts'],
    rules: { 'no-restricted-globals': 'off' },
  },
);
