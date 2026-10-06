-- V6: incerteza exatamente como a fonte publicou.
--
-- original_value/original_unit ja preservam o valor; faltava o mesmo para a
-- incerteza. Sem isto, uma incerteza publicada mas invalida (ex. plx_err < 0)
-- vira err_minus/err_plus NULL e e indistinguivel de "a fonte nao publicou
-- incerteza" — a API diria "Paralaxe sem incerteza publicada", o que e falso.
-- Agora: err_* NULL + original_error preenchido = incerteza publicada e descartada
-- por invalida (motivo PARALLAX_UNCERTAINTY_INVALID na leitura).
--
-- Formato: texto da coluna de erro como veio; para pares assimetricos
-- (mesDistance) 'minus_err=<texto>; plus_err=<texto>'.

ALTER TABLE measurement ADD COLUMN original_error text;
