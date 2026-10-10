package com.sarvaai.app.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.sarvaai.app.data.entities.*

@Dao
interface MenuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenu(menu: MenuEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<MenuSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MenuItemEntity>)

    @Transaction
    suspend fun insertFullMenu(
        menu: MenuEntity,
        sections: List<MenuSectionEntity>,
        items: List<MenuItemEntity>
    ) {
        insertMenu(menu)
        insertSections(sections)
        insertItems(items)
    }

    @Query("SELECT * FROM menus WHERE menuId = :menuId")
    suspend fun getMenuById(menuId: String): MenuEntity?

    @Query("SELECT * FROM menus ORDER BY captureTimestamp DESC LIMIT 1")
    fun getLatestMenu(): Flow<MenuEntity?>

    @Query("SELECT * FROM menu_sections WHERE menuId = :menuId ORDER BY displayOrder ASC")
    fun getSectionsForMenu(menuId: String): Flow<List<MenuSectionEntity>>

    @Query("SELECT * FROM menu_sections WHERE menuId = :menuId ORDER BY displayOrder ASC")
    suspend fun getSectionsForMenuDirect(menuId: String): List<MenuSectionEntity>

    @Query("SELECT * FROM menu_items WHERE sectionId = :sectionId")
    fun getItemsForSection(sectionId: String): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE sectionId = :sectionId")
    suspend fun getItemsForSectionDirect(sectionId: String): List<MenuItemEntity>

    @Query("SELECT * FROM menu_items WHERE isVegetarian = 1")
    fun getVegetarianItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE isJain = 1")
    fun getJainItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE itemId = :itemId")
    suspend fun getItemById(itemId: String): MenuItemEntity?

    @Query("SELECT * FROM menu_items")
    suspend fun getAllItemsDirect(): List<MenuItemEntity>

    @Query("DELETE FROM menus")
    suspend fun clearMenus()
}
