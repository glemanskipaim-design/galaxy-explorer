import { Link } from 'react-router'
import type { CategoryObjectItem } from '../../api/types'
import styles from './Category.module.css'

export function ObjectList({ items }: { items: CategoryObjectItem[] }) {
  return (
    <ul className={styles.grid}>
      {items.map((item) => (
        <li key={item.slug}>
          <Link className={styles.item} to={`/objects/${encodeURIComponent(item.slug)}`}>
            <span className={styles.itemName}>{item.displayName}</span>
            <span className={styles.itemCategory}>{item.categoryName ?? 'Sem categoria'}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
