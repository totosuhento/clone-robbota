package com.robotta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.robotta.data.entities.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts")
    fun observeAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Int): Flow<Account?>

    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Int): Account?

    @Query("SELECT * FROM accounts WHERE id = 1")
    suspend fun getProfile(): Account?

    @Query("SELECT * FROM accounts WHERE id = 1")
    fun observeProfile(): Flow<Account?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(account: Account)

    @Query("UPDATE accounts SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE accounts SET isActive = 1, lastUsedAt = :now WHERE id = :id")
    suspend fun setActive(id: Int, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM accounts WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Account?

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Int)
}