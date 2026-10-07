import js from '@eslint/js'
import { defineConfig, globalIgnores } from 'eslint/config'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import globals from 'globals'
import tseslint from 'typescript-eslint'

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'node_modules']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommendedTypeChecked,
      reactHooks.configs.flat['recommended-latest'],
      reactRefresh.configs.vite,
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      // Recomendacao do yagyu (review-log do Bloco 1) forcada por lint, nao so combinada:
      // texto vindo das fontes (nomes, mensagens, acknowledgments) e sempre texto React.
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'Proibido: renderize texto vindo da API como texto React (nunca HTML cru).',
        },
        {
          selector: 'MemberExpression[property.name=/^(innerHTML|outerHTML)$/]',
          message: 'Proibido: innerHTML/outerHTML abrem a porta para HTML vindo das fontes.',
        },
        {
          selector: 'MemberExpression[computed=true][property.value=/^(innerHTML|outerHTML)$/]',
          message: "Proibido: el['innerHTML'] / el['outerHTML'] sao o mesmo HTML cru.",
        },
        {
          selector: 'CallExpression[callee.property.name=/^(insertAdjacentHTML|write|writeln)$/]',
          message: 'Proibido: insertAdjacentHTML / document.write(ln) inserem HTML cru.',
        },
      ],
    },
  },
  {
    files: ['src/test/**/*.{ts,tsx}', 'src/**/*.test.{ts,tsx}'],
    rules: {
      // Componentes auxiliares de teste nao participam do HMR.
      'react-refresh/only-export-components': 'off',
    },
  },
  {
    files: ['**/*.{js,mjs}'],
    extends: [js.configs.recommended],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: globals.node,
    },
  },
])
