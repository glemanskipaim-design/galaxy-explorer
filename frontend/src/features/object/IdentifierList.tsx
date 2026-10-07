import type { Identifier } from '../../api/types'
import { commonNameLabel, groupByCatalog } from '../../domain/identifiers'
import styles from './Object.module.css'

/** Nomes comuns em destaque; todos os identificadores (como vieram) agrupados por catalogo. */
export function IdentifierList({ identifiers }: { identifiers: Identifier[] }) {
  const common = identifiers.filter((i) => i.isCommonName)
  if (identifiers.length === 0) return null
  return (
    <div className={styles.identifiers}>
      {common.length > 0 && (
        <p className={styles.aka}>
          <span className={styles.akaLabel}>Também conhecido como</span>
          {common.map((i) => (
            <span key={i.identifier} className={styles.akaName}>
              {commonNameLabel(i.identifier)}
            </span>
          ))}
        </p>
      )}
      <details className={styles.identifierDetails}>
        <summary>
          {identifiers.length} {identifiers.length === 1 ? 'identificador' : 'identificadores'} em catálogos
        </summary>
        <dl className={styles.catalogs}>
          {groupByCatalog(identifiers).map((g) => (
            <div key={g.catalog}>
              <dt>{g.catalog}</dt>
              <dd>
                {g.identifiers.map((i) => (
                  <code key={i.identifier} className={styles.ident}>
                    {i.identifier}
                  </code>
                ))}
              </dd>
            </div>
          ))}
        </dl>
      </details>
    </div>
  )
}
