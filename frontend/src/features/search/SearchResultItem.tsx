import { Link } from 'react-router'
import type { SearchHit } from '../../api/types'
import styles from './Search.module.css'

export function SearchResultItem({ hit }: { hit: SearchHit }) {
  return (
    <li>
      <Link className={styles.hit} to={`/objects/${encodeURIComponent(hit.slug)}`}>
        <span className={styles.hitMain}>
          <span className={styles.hitName}>{hit.displayName}</span>
          <span className={styles.hitCategory}>{hit.categoryName ?? 'Sem categoria'}</span>
        </span>
        <span className={styles.hitMeta}>
          <span>
            corresponde a: <span className={styles.hitIdent}>{hit.matchedIdentifier}</span>
          </span>
          {hit.matchType === 'FUZZY' && (
            <span className={styles.fuzzy} title="Grafia parecida, não idêntica">
              aproximado
            </span>
          )}
        </span>
        <span className={styles.hitArrow} aria-hidden="true">
          →
        </span>
      </Link>
    </li>
  )
}
