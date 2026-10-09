import { useMemo } from 'react'
import { Link, useParams } from 'react-router'
import { ApiRequestError } from '../api/http'
import { useObject } from '../api/queries'
import type { ObjectDetail } from '../api/types'
import { ErrorState } from '../components/ui/ErrorState'
import { LoadingState } from '../components/ui/LoadingState'
import { groupProperties, indexMeasurements } from '../domain/displayGroups'
import { ImageUnavailable } from '../features/image/ImageUnavailable'
import { ObjectImages } from '../features/image/ObjectImages'
import objectStyles from '../features/object/Object.module.css'
import { ObjectHeader } from '../features/object/ObjectHeader'
import { PositionCard } from '../features/object/PositionCard'
import { PropertyGroupCard } from '../features/object/PropertyGroupCard'
import { SourcesSection } from '../features/object/SourcesSection'
import pageStyles from './Pages.module.css'

function ObjectDetailView({ detail }: { detail: ObjectDetail }) {
  const groups = useMemo(() => groupProperties(detail.properties), [detail.properties])
  const byId = useMemo(() => indexMeasurements(detail.properties), [detail.properties])
  return (
    <article>
      <title>{`${detail.displayName} · Galaxy Explorer`}</title>
      <div className={objectStyles.top}>
        <ObjectHeader detail={detail} />
        {detail.images.length > 0 ? (
          <ObjectImages images={detail.images} displayName={detail.displayName} />
        ) : (
          <ImageUnavailable absence={detail.imageAbsence} />
        )}
      </div>
      <div className={objectStyles.cards}>
        <PositionCard position={detail.position} />
        {groups.map((g) => (
          <PropertyGroupCard key={g.group} label={g.label} properties={g.properties} byId={byId} />
        ))}
      </div>
      <SourcesSection sources={detail.sources} />
    </article>
  )
}

export function ObjectPage() {
  const { slug = '' } = useParams()
  const object = useObject(slug)

  if (object.isPending) return <LoadingState label="Carregando o objeto…" />
  if (object.isError) {
    if (object.error instanceof ApiRequestError && object.error.status === 404) {
      return (
        <div className={pageStyles.notFound}>
          <title>Objeto não encontrado · Galaxy Explorer</title>
          <h1>Este objeto não está no catálogo local</h1>
          <p>Você pode procurá-lo pela busca — se existir no SIMBAD, dá para importar.</p>
          <Link to={`/?q=${encodeURIComponent(slug)}`}>Buscar “{slug}”</Link>
        </div>
      )
    }
    return <ErrorState error={object.error} onRetry={() => void object.refetch()} />
  }
  return <ObjectDetailView detail={object.data} />
}
