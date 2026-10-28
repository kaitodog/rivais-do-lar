package com.rivaisdolar.app.data

import com.rivaisdolar.app.data.model.Registro
import com.rivaisdolar.app.data.model.Tarefa
import com.rivaisdolar.app.data.model.Temporada
import com.rivaisdolar.app.data.remote.JsonRivais
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatos que o PostgREST devolve (numeric como número, jsonb como objeto, timestamptz ISO). */
class ModelosTest {
    @Test
    fun `lê tarefa do PostgREST`() {
        val t = JsonRivais.decodeFromString(
            Tarefa.serializer(),
            """{"id":"t1","casa_id":"c1","titulo":"Lavar a louça","categoria":"Cozinha","icone":"restaurant","minutos":20,
               "esforco":1,"chatice":1.3,"recorrencia":"diaria","prazo":"2026-10-28T23:59:00-03:00","status":"livre",
               "reservada_por":null,"reservada_em":null,"dias_parada":0,"ativa":true,"concluida_em":null,
               "criada_por":"u1","criada_em":"2026-10-27T10:00:00.123456+00:00"}""",
        )
        assertEquals(1.3, t.chatice, 0.0)
        assertEquals("livre", t.status)
    }

    @Test
    fun `lê placar jsonb e multiplicadores`() {
        val temp = JsonRivais.decodeFromString(
            Temporada.serializer(),
            """{"id":"s1","casa_id":"c1","numero":7,"inicio":"2026-10-26T03:00:00+00:00","fim":"2026-11-02T01:00:00+00:00",
               "placar":{"u1":1240,"u2":1185},"aposta":null,"meta_alvo":2000,"meta_atual":1445,"meta_premio":"pizza",
               "vencedor":null,"premio_resgatado":false,"encerrada":false,"encerrada_em":null}""",
        )
        assertEquals(1240, temp.placar["u1"])
        val r = JsonRivais.decodeFromString(
            Registro.serializer(),
            """{"id":"r1","casa_id":"c1","tarefa_id":"t1","user_id":"u1","temporada_id":null,"titulo":"Banheiro",
               "pontos_base":135,"multiplicadores":[{"tipo":"bomba","rotulo":"Bomba · parada há 3 dias","valor":1.3}],
               "pontos_finais":175,"moedas":17,"foto_path":null,"status":"pendente","comentario":null,"contestado_por":null,
               "contestado_em":null,"validado_por":null,"validado_em":null,"criado_em":"2026-10-28T21:00:00+00:00"}""",
        )
        assertEquals(1.3, r.multiplicadores.single().valor, 0.0)
    }
}
