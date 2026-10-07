import type { Category, SourceType } from '../../api/types'
import styles from './Object.module.css'

interface ClassificationExplainerProps {
  category: Category | null
  sourceTypes: SourceType[]
}

/** Disclosure nativo (funciona no celular, onde tooltip de hover nao existe). */
export function ClassificationExplainer({ category, sourceTypes }: ClassificationExplainerProps) {
  if (!category?.basis && sourceTypes.length === 0) return null
  return (
    <details className={styles.explainer}>
      <summary>Por que esta classificação?</summary>
      <div className={styles.explainerBody}>
        {sourceTypes.length > 0 && (
          <ul className={styles.sourceTypes}>
            {sourceTypes.map((t) => (
              <li key={`${t.source}/${t.sourceRef ?? ''}`}>
                {t.source}: tipo <code>{t.sourceType}</code>
                {t.morphType && (
                  <>
                    , morfologia <code>{t.morphType}</code>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
        {category?.basis && (
          <p className={styles.basis}>
            Regra aplicada: <code>{category.basis}</code>
          </p>
        )}
      </div>
    </details>
  )
}
