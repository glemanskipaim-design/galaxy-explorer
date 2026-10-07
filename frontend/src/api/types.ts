// Espelho dos DTOs do backend. Cada tipo cita o arquivo Java de origem.
// Regras: campo que pode ser null no Java -> `T | null` (o Jackson serializa null explicito);
// Instant -> string ISO-8601 (conferido nas fixtures); enums do backend -> vocabulario ABERTO
// (`'a' | 'b' | (string & {})`): valor novo no backend nao quebra a UI (ha fallback), e a fixture
// JSON (tipo inferido `string`) continua atribuivel — e isso que deixa o tsc verificar o contrato.

/** catalog/read/MatchType.java (serializado com .name()) */
export type MatchType = 'EXACT' | 'FUZZY' | (string & {})

/** api/search/SearchHitDto.java */
export interface SearchHit {
  slug: string
  displayName: string
  categoryCode: string | null
  categoryName: string | null
  matchedIdentifier: string
  matchType: MatchType
  score: number
}

/** api/search/SearchResponse.java */
export interface SearchResponse {
  query: string
  normalizedQuery: string
  hits: SearchHit[]
}

/** api/imports/CreateImportRequest.java (charset validado so no backend) */
export interface CreateImportRequest {
  query: string
}

/** ingestion/jobs/ImportJobStatus.java (dbValue() minusculo) */
export type ImportJobStatus = 'queued' | 'running' | 'succeeded' | 'not_found' | 'failed' | (string & {})

/** api/imports/ImportJobResponse.java */
export interface ImportJob {
  id: number
  query: string
  status: ImportJobStatus
  message: string | null
  resultSlugs: string[]
  runId: number | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

/** ObjectDetailResponse.Category */
export interface Category {
  code: string
  namePt: string
  nameEn: string
  basis: string | null
}

/** ObjectDetailResponse.SourceType */
export interface SourceType {
  source: string
  sourceRef: string | null
  sourceType: string
  morphType: string | null
}

/** ObjectDetailResponse.Identifier — o JSON usa `isCommonName` (conferido na fixture) */
export interface Identifier {
  identifier: string
  catalog: string | null
  isCommonName: boolean
  /** null = alias curado */
  source: string | null
}

/** ObjectDetailResponse.Reference (+ ObjectDetailMapper.reference) */
export interface Reference {
  bibcode: string
  adsUrl: string
}

/** ObjectDetailResponse.Position */
export interface Position {
  raDeg: number
  decDeg: number
  frame: string
  epoch: string
  source: string
  reference: Reference | null
  retrievedAt: string
}

/** catalog/read/AbsenceReason.java */
export type AbsenceCode =
  | 'NOT_PROVIDED_BY_SOURCE'
  | 'PARALLAX_SNR_BELOW_5'
  | 'PARALLAX_WITHOUT_UNCERTAINTY'
  | 'PARALLAX_UNCERTAINTY_INVALID'
  | 'PARALLAX_NON_POSITIVE'
  | (string & {})

/** ObjectDetailResponse.Absence */
export interface Absence {
  code: AbsenceCode
  message: string
  snr: number | null
}

/** catalog/Epistemic.java (dbValue()) — note o quinto valor, `model` */
export type Epistemic =
  | 'observed'
  | 'derived_by_source'
  | 'computed_by_us'
  | 'estimated'
  | 'model'
  | (string & {})

/** ObjectDetailResponse.Measurement (de catalog/read/MeasurementView.java) */
export interface Measurement {
  id: number
  value: number | null
  errMinus: number | null
  errPlus: number | null
  limitFlag: '<' | '>' | '~' | (string & {}) | null
  valueText: string | null
  /** unidade CANONICA da propriedade (property_definition.canonical_unit): pc, mas, km/s, mag, arcmin ou null */
  unit: string | null
  originalValue: string
  originalUnit: string | null
  originalError: string | null
  epistemic: Epistemic
  method: string | null
  derivedFrom: number[] | null
  source: string
  dataset: string
  sourceUrl: string
  reference: Reference | null
  retrievedAt: string
}

/** ObjectDetailResponse.Property (montado em catalog/read/ObjectQueryService.buildProperties) */
export interface Property {
  code: string
  label: string
  group: string
  qualifier: string
  available: boolean
  absence: Absence | null
  preferred: Measurement | null
  alternatives: Measurement[]
}

/** ObjectDetailResponse.Source */
export interface Source {
  code: string
  name: string
  homepageUrl: string
  termsUrl: string | null
  acknowledgment: string | null
}

/** api/object/ObjectDetailResponse.java */
export interface ObjectDetail {
  slug: string
  displayName: string
  category: Category | null
  existenceStatus: string
  sourceTypes: SourceType[]
  identifiers: Identifier[]
  position: Position | null
  properties: Property[]
  sources: Source[]
}

/** api/error/ApiErrorCode.java */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'OBJECT_NOT_FOUND'
  | 'IMPORT_NOT_FOUND'
  | 'IMPORT_QUEUE_FULL'
  | 'CATEGORY_NOT_FOUND'
  | 'INTERNAL_ERROR'
  | (string & {})

export interface ApiErrorDetail {
  field: string
  message: string
}

/** api/error/ApiError.java */
export interface ApiErrorBody {
  error: {
    code: ApiErrorCode
    message: string
    details: ApiErrorDetail[] | null
  }
}

/** api/category/CategoryListResponse.Item */
export interface CategorySummary {
  code: string
  namePt: string
  nameEn: string
  objectCount: number
}

/** api/category/CategoryListResponse.java */
export interface CategoryListResponse {
  categories: CategorySummary[]
}

/** api/category/CategoryObjectsResponse.Item */
export interface CategoryObjectItem {
  slug: string
  displayName: string
  categoryCode: string | null
  categoryName: string | null
}

/** api/category/CategoryObjectsResponse.java */
export interface CategoryObjectsResponse {
  category: { code: string; namePt: string; nameEn: string }
  total: number
  limit: number
  offset: number
  items: CategoryObjectItem[]
}
