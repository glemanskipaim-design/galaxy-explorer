import { useCategories } from '../../api/queries'
import { CATEGORY_SHORTCUTS, resolveShortcuts } from '../../domain/categoryShortcuts'
import { CategoryChip } from './CategoryChip'
import styles from './Home.module.css'

/**
 * Atalhos por categoria. Ativo SSE o backend diz que ha objetos (GET /categories). Carregando:
 * esqueletos com aria-busy. Erro: todos desabilitados "indisponivel" — nunca ativar no escuro.
 */
export function CategoryShortcuts() {
  const categories = useCategories()

  return (
    <section className={styles.shortcuts} aria-labelledby="atalhos-titulo" aria-busy={categories.isPending}>
      <h2 id="atalhos-titulo" className={styles.sectionTitle}>
        Explorar por categoria
      </h2>
      {categories.isPending ? (
        <ul className={styles.chips}>
          {CATEGORY_SHORTCUTS.map((s) => (
            <li key={s.label}>
              <span className={styles.chipSkeleton}>{s.label}</span>
            </li>
          ))}
        </ul>
      ) : (
        <ul className={styles.chips}>
          {resolveShortcuts(CATEGORY_SHORTCUTS, categories.data?.categories ?? null).map((s) => (
            <li key={s.label}>
              <CategoryChip {...s} />
            </li>
          ))}
        </ul>
      )}
      {categories.isError && (
        <p className={styles.shortcutsError} role="alert">
          Não foi possível carregar as categorias; os atalhos ficam indisponíveis.
        </p>
      )}
    </section>
  )
}
