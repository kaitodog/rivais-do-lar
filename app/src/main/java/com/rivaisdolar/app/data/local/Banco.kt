package com.rivaisdolar.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Última fotografia da casa vinda do servidor, para abrir o app sem rede. */
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val chave: String,
    val json: String,
    val atualizadoEm: Long,
)

/** Ação feita sem conexão, reenviada quando a rede volta (chamada RPC com parâmetros JSON). */
@Entity(tableName = "acoes_pendentes")
data class AcaoPendenteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val funcao: String,
    val parametros: String,
    val descricao: String,
    val criadaEm: Long,
)

@Dao
interface CacheDao {
    @Query("select * from cache where chave = :chave")
    suspend fun buscar(chave: String): CacheEntity?

    @Upsert
    suspend fun salvar(entidade: CacheEntity)

    @Query("delete from cache")
    suspend fun limpar()
}

@Dao
interface AcaoPendenteDao {
    @Insert
    suspend fun inserir(acao: AcaoPendenteEntity)

    @Query("select * from acoes_pendentes order by id")
    suspend fun todas(): List<AcaoPendenteEntity>

    @Query("select count(*) from acoes_pendentes")
    fun contagem(): Flow<Int>

    @Delete
    suspend fun remover(acao: AcaoPendenteEntity)

    @Query("delete from acoes_pendentes")
    suspend fun limpar()
}

@Database(entities = [CacheEntity::class, AcaoPendenteEntity::class], version = 1, exportSchema = true)
abstract class BancoLocal : RoomDatabase() {
    abstract fun cache(): CacheDao
    abstract fun acoes(): AcaoPendenteDao
}
