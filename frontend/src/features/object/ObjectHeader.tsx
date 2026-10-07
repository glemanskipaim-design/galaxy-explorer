import { Link } from 'react-router'
import type { ObjectDetail } from '../../api/types'
import { ClassificationExplainer } from './ClassificationExplainer'
import { IdentifierList } from './IdentifierList'
import styles from './Object.module.css'

export function ObjectHeader({ detail }: { detail: ObjectDetail }) {
  return (
    <header className={styles.header}>
      <p className={styles.kicker}>
        {detail.category ? (
          <Link to={`/categories/${encodeURIComponent(detail.category.code)}`} className={styles.categoryPill}>
            {detail.category.namePt}
          </Link>
        ) : (
          <span className={styles.categoryPill}>Sem classificação</span>
        )}
        {detail.existenceStatus !== 'confirmed' && (
          <span className={styles.existence}>Status: {detail.existenceStatus}</span>
        )}
      </p>
      <h1 className={styles.name}>{detail.displayName}</h1>
      <ClassificationExplainer category={detail.category} sourceTypes={detail.sourceTypes} />
      <IdentifierList identifiers={detail.identifiers} />
    </header>
  )
}
