import type { Position } from '../../api/types'
import { formatDec, formatDegrees, formatRa } from '../../domain/format/coordinates'
import styles from './Object.module.css'
import { ProvenanceLine } from './ProvenanceLine'

export function PositionCard({ position }: { position: Position | null }) {
  return (
    <section className={styles.card} aria-labelledby="posicao-titulo">
      <h2 id="posicao-titulo" className={styles.cardTitle}>
        Posição no céu
      </h2>
      {position === null ? (
        <p className={styles.absence}>
          <span aria-hidden="true" className={styles.absenceIcon}>
            ∅
          </span>
          Posição não disponível na fonte
        </p>
      ) : (
        <>
          <dl className={styles.coords}>
            <div>
              <dt>Ascensão reta</dt>
              <dd>
                <span className={styles.coordMain}>{formatRa(position.raDeg)}</span>
                <span className={styles.coordSub}>{formatDegrees(position.raDeg)}</span>
              </dd>
            </div>
            <div>
              <dt>Declinação</dt>
              <dd>
                <span className={styles.coordMain}>{formatDec(position.decDeg)}</span>
                <span className={styles.coordSub}>{formatDegrees(position.decDeg)}</span>
              </dd>
            </div>
          </dl>
          <p className={styles.frame}>
            Referencial {position.frame} · época {position.epoch}
          </p>
          <ProvenanceLine source={position.source} reference={position.reference} retrievedAt={position.retrievedAt} />
        </>
      )}
    </section>
  )
}
