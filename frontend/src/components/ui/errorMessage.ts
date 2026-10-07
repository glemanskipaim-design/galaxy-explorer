import { ApiRequestError } from '../../api/http'

const NETWORK_MESSAGE = 'Não foi possível falar com o servidor. Verifique se o backend está rodando.'

/** Mensagem de erro para o usuario: o texto da API (pt-BR) quando houver, senao uma generica de rede. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const detail = error.details?.[0]?.message
    return detail ? `${error.message}: ${detail}` : error.message
  }
  return NETWORK_MESSAGE
}
