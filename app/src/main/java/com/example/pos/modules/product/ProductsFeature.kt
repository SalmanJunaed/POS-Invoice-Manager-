package com.example.pos.modules.product

import com.example.pos.core.error.PosResult
import com.example.pos.data.db.ProductEntity
import com.example.pos.data.repository.ProductRepository
import kotlinx.coroutines.flow.Flow

/**
 * Modular foundation for Products & Services Catalog (Module 4).
 */
interface ProductFeatureFacade {
    val activeProductsFlow: Flow<List<ProductEntity>>
    val allProductsFlow: Flow<List<ProductEntity>>
    suspend fun saveProduct(
        id: String?,
        name: String,
        type: String,
        unit: String,
        unitPricePaisa: Long,
        sortOrder: Int
    ): PosResult<ProductEntity>
    suspend fun deleteProduct(id: String): PosResult<Unit>
    suspend fun reorder(id: String, newOrder: Int): PosResult<Unit>
}

class ProductFeatureFacadeImpl(private val productRepository: ProductRepository) : ProductFeatureFacade {
    override val activeProductsFlow = productRepository.activeProducts
    override val allProductsFlow = productRepository.allProducts
    override suspend fun saveProduct(
        id: String?,
        name: String,
        type: String,
        unit: String,
        unitPricePaisa: Long,
        sortOrder: Int
    ) = productRepository.saveProduct(id, name, type, unit, unitPricePaisa, sortOrder)

    override suspend fun deleteProduct(id: String) = productRepository.deleteProduct(id)
    override suspend fun reorder(id: String, newOrder: Int) = productRepository.updateSortOrder(id, newOrder)
}
