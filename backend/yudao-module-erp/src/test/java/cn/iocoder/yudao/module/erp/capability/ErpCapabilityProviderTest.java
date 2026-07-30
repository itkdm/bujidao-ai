package cn.iocoder.yudao.module.erp.capability;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpProductSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpProductSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderCreateReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStatisticsSummaryDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStatisticsSummaryReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockQueryDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockQueryReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockRecordSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockRecordSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseSearchReqDTO;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.order.ErpSaleOrderPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.stock.ErpStockPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.warehouse.ErpWarehousePageReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockRecordDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.yudao.module.erp.enums.stock.ErpStockRecordBizTypeEnum;
import cn.iocoder.yudao.module.erp.service.product.ErpProductService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.yudao.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.yudao.module.erp.service.sale.ErpSaleOrderService;
import cn.iocoder.yudao.module.erp.service.statistics.ErpPurchaseStatisticsService;
import cn.iocoder.yudao.module.erp.service.statistics.ErpSaleStatisticsService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockRecordService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockService;
import cn.iocoder.yudao.module.erp.service.stock.ErpWarehouseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ErpCapabilityProvider} 的单元测试
 *
 * @author bujidao
 */
class ErpCapabilityProviderTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ErpCapabilityProvider provider;

    @Mock
    private ErpProductService productService;
    @Mock
    private ErpStockService stockService;
    @Mock
    private ErpWarehouseService warehouseService;
    @Mock
    private ErpCustomerService customerService;
    @Mock
    private ErpSaleOrderService saleOrderService;
    @Mock
    private ErpPurchaseOrderService purchaseOrderService;
    @Mock
    private ErpSupplierService supplierService;
    @Mock
    private ErpStockRecordService stockRecordService;
    @Mock
    private ErpSaleStatisticsService saleStatisticsService;
    @Mock
    private ErpPurchaseStatisticsService purchaseStatisticsService;

    @Test
    void searchProducts_shouldMapKeywordToNameAndSuggestStockQuery() {
        ErpProductRespVO product = new ErpProductRespVO();
        product.setId(101L);
        product.setName("测试商品");
        product.setBarCode("BC-101");
        product.setCategoryId(9L);
        product.setCategoryName("默认分类");
        product.setUnitName("个");
        product.setStatus(0);
        product.setPurchasePrice(new BigDecimal("10.00"));
        product.setSalePrice(new BigDecimal("18.00"));
        when(productService.getProductVOPage(any())).thenReturn(new PageResult<>(List.of(product), 1L));

        ErpProductSearchReqDTO reqDTO = new ErpProductSearchReqDTO();
        reqDTO.setKeyword("测试");
        reqDTO.setCategoryId(9L);
        CapabilityResult result = provider.searchProducts(reqDTO);

        ArgumentCaptor<ErpProductPageReqVO> captor = ArgumentCaptor.forClass(ErpProductPageReqVO.class);
        verify(productService).getProductVOPage(captor.capture());
        assertEquals("测试", captor.getValue().getName());
        assertEquals(9L, captor.getValue().getCategoryId());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(10, captor.getValue().getPageSize());

        assertTrue(result.isSuccess());
        ErpProductSearchDataDTO data = (ErpProductSearchDataDTO) result.getData();
        assertEquals(1L, data.getTotal());
        assertEquals(1, data.getReturnedCount());
        assertEquals("测试商品", data.getList().get(0).getName());
        assertEquals("BC-101", data.getList().get(0).getBarCode());
        // 有候选商品时提示下一步查询库存
        assertEquals(1, result.getSuggestedNextActions().size());
        assertEquals("erp.stock.query", result.getSuggestedNextActions().get(0).getName());
    }

    @Test
    void searchProducts_shouldClampPageSizeToUpperBound() {
        when(productService.getProductVOPage(any())).thenReturn(PageResult.empty());

        ErpProductSearchReqDTO reqDTO = new ErpProductSearchReqDTO();
        reqDTO.setPageSize(500);
        CapabilityResult result = provider.searchProducts(reqDTO);

        ArgumentCaptor<ErpProductPageReqVO> captor = ArgumentCaptor.forClass(ErpProductPageReqVO.class);
        verify(productService).getProductVOPage(captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
        // 无候选商品时不给出下一步建议，避免 Agent 拿着空结果继续调用
        assertTrue(result.getSuggestedNextActions().isEmpty());
    }

    @Test
    void queryStock_shouldAggregateAllWarehousesWithNames() {
        ErpStockDO stockA = new ErpStockDO();
        stockA.setProductId(101L);
        stockA.setWarehouseId(1L);
        stockA.setCount(new BigDecimal("6"));
        ErpStockDO stockB = new ErpStockDO();
        stockB.setProductId(101L);
        stockB.setWarehouseId(2L);
        stockB.setCount(null);
        when(stockService.getStockPage(any())).thenReturn(new PageResult<>(List.of(stockA, stockB), 2L));
        ErpWarehouseDO warehouse = new ErpWarehouseDO();
        warehouse.setId(1L);
        warehouse.setName("主仓");
        when(warehouseService.getWarehouseMap(any())).thenReturn(Map.of(1L, warehouse));
        when(stockService.getStockCount(101L)).thenReturn(new BigDecimal("6"));

        ErpStockQueryReqDTO reqDTO = new ErpStockQueryReqDTO();
        reqDTO.setProductId(101L);
        CapabilityResult result = provider.queryStock(reqDTO);

        assertTrue(result.isSuccess());
        ErpStockQueryDataDTO data = (ErpStockQueryDataDTO) result.getData();
        assertEquals(new BigDecimal("6"), data.getTotalCount());
        assertEquals(2, data.getWarehouseStocks().size());
        assertEquals("主仓", data.getWarehouseStocks().get(0).getWarehouseName());
        // 缺少仓库档案时仓库名为空，数量兜底为 0，不抛异常
        assertNull(data.getWarehouseStocks().get(1).getWarehouseName());
        assertEquals(BigDecimal.ZERO, data.getWarehouseStocks().get(1).getCount());
        assertEquals(1, result.getEvidence().size());
        assertEquals("stock_snapshot", result.getEvidence().get(0).getCode());
    }

    @Test
    void queryStock_shouldSumOnlyRequestedWarehouse() {
        ErpStockDO stock = new ErpStockDO();
        stock.setProductId(101L);
        stock.setWarehouseId(2L);
        stock.setCount(new BigDecimal("4"));
        when(stockService.getStockPage(any())).thenReturn(new PageResult<>(List.of(stock), 1L));
        when(warehouseService.getWarehouseMap(any())).thenReturn(Map.of());

        ErpStockQueryReqDTO reqDTO = new ErpStockQueryReqDTO();
        reqDTO.setProductId(101L);
        reqDTO.setWarehouseId(2L);
        CapabilityResult result = provider.queryStock(reqDTO);

        ArgumentCaptor<ErpStockPageReqVO> captor = ArgumentCaptor.forClass(ErpStockPageReqVO.class);
        verify(stockService).getStockPage(captor.capture());
        assertEquals(101L, captor.getValue().getProductId());
        assertEquals(2L, captor.getValue().getWarehouseId());

        ErpStockQueryDataDTO data = (ErpStockQueryDataDTO) result.getData();
        // 指定仓库时合计只汇总该仓库，不能取全仓库存
        assertEquals(new BigDecimal("4"), data.getTotalCount());
        verify(stockService, org.mockito.Mockito.never()).getStockCount(any());
    }

    @Test
    void searchWarehouses_shouldPassKeywordAndStatus() {
        ErpWarehouseDO warehouse = new ErpWarehouseDO();
        warehouse.setId(1L);
        warehouse.setName("主仓");
        warehouse.setAddress("上海");
        warehouse.setPrincipal("张三");
        warehouse.setStatus(0);
        warehouse.setDefaultStatus(true);
        when(warehouseService.getWarehousePage(any())).thenReturn(new PageResult<>(List.of(warehouse), 1L));

        ErpWarehouseSearchReqDTO reqDTO = new ErpWarehouseSearchReqDTO();
        reqDTO.setKeyword("主");
        reqDTO.setStatus(0);
        CapabilityResult result = provider.searchWarehouses(reqDTO);

        ArgumentCaptor<ErpWarehousePageReqVO> captor = ArgumentCaptor.forClass(ErpWarehousePageReqVO.class);
        verify(warehouseService).getWarehousePage(captor.capture());
        assertEquals("主", captor.getValue().getName());
        assertEquals(0, captor.getValue().getStatus());

        ErpWarehouseSearchDataDTO data = (ErpWarehouseSearchDataDTO) result.getData();
        assertEquals(1, data.getReturnedCount());
        assertEquals("主仓", data.getList().get(0).getName());
    }

    @Test
    void searchCustomers_shouldNotCopyKeywordIntoPhoneFields() {
        ErpCustomerDO customer = new ErpCustomerDO();
        customer.setId(1L);
        customer.setName("张三");
        customer.setContact("张三");
        customer.setMobile("13800000000");
        customer.setBankAccount("6222000000000000");
        when(customerService.getCustomerPage(any())).thenReturn(new PageResult<>(List.of(customer), 1L));

        ErpCustomerSearchReqDTO reqDTO = new ErpCustomerSearchReqDTO();
        reqDTO.setKeyword("张三");
        CapabilityResult result = provider.searchCustomers(reqDTO);

        ArgumentCaptor<ErpCustomerPageReqVO> captor = ArgumentCaptor.forClass(ErpCustomerPageReqVO.class);
        verify(customerService).getCustomerPage(captor.capture());
        // keyword 只映射到 name，否则底层 Mapper 会把 name/mobile 做 AND 组合导致查不到数据
        assertEquals("张三", captor.getValue().getName());
        assertNull(captor.getValue().getMobile());
        assertNull(captor.getValue().getTelephone());

        ErpCustomerSearchDataDTO data = (ErpCustomerSearchDataDTO) result.getData();
        assertEquals("张三", data.getList().get(0).getName());
        assertEquals("13800000000", data.getList().get(0).getMobile());
    }

    @Test
    void searchSuppliers_shouldReturnSupplierView() {
        ErpSupplierDO supplier = new ErpSupplierDO();
        supplier.setId(1L);
        supplier.setName("供应商甲");
        supplier.setContact("李四");
        supplier.setMobile("13900000000");
        supplier.setTelephone("021-00000000");
        supplier.setStatus(0);
        when(supplierService.getSupplierPage(any())).thenReturn(new PageResult<>(List.of(supplier), 1L));

        ErpSupplierSearchReqDTO reqDTO = new ErpSupplierSearchReqDTO();
        reqDTO.setKeyword("供应商");
        reqDTO.setMobile("139");
        CapabilityResult result = provider.searchSuppliers(reqDTO);

        ArgumentCaptor<ErpSupplierPageReqVO> captor = ArgumentCaptor.forClass(ErpSupplierPageReqVO.class);
        verify(supplierService).getSupplierPage(captor.capture());
        assertEquals("供应商", captor.getValue().getName());
        assertEquals("139", captor.getValue().getMobile());

        ErpSupplierSearchDataDTO data = (ErpSupplierSearchDataDTO) result.getData();
        assertEquals("供应商甲", data.getList().get(0).getName());
        verifyNoInteractions(productService, stockService, customerService);
    }

    @Test
    void searchSaleOrders_shouldPassFiltersAndIncludeCustomerName() {
        ErpSaleOrderDO saleOrder = new ErpSaleOrderDO();
        saleOrder.setId(11L);
        saleOrder.setNo("XS-001");
        saleOrder.setCustomerId(21L);
        saleOrder.setTotalCount(new BigDecimal("2"));
        saleOrder.setTotalPrice(new BigDecimal("36.00"));
        when(saleOrderService.getSaleOrderPage(any())).thenReturn(new PageResult<>(List.of(saleOrder), 1L));
        ErpCustomerDO customer = new ErpCustomerDO();
        customer.setId(21L);
        customer.setName("测试客户");
        when(customerService.getCustomerMap(any())).thenReturn(Map.of(21L, customer));
        when(saleOrderService.getSaleOrderItemListByOrderId(11L)).thenReturn(List.of());
        when(productService.getProductVOMap(any())).thenReturn(Map.of());

        ErpSaleOrderSearchReqDTO reqDTO = new ErpSaleOrderSearchReqDTO();
        reqDTO.setNo("XS");
        reqDTO.setCustomerId(21L);
        reqDTO.setProductId(101L);
        reqDTO.setStatus(20);
        CapabilityResult result = provider.searchSaleOrders(reqDTO);

        ArgumentCaptor<ErpSaleOrderPageReqVO> captor = ArgumentCaptor.forClass(ErpSaleOrderPageReqVO.class);
        verify(saleOrderService).getSaleOrderPage(captor.capture());
        assertEquals("XS", captor.getValue().getNo());
        assertEquals(21L, captor.getValue().getCustomerId());
        assertEquals(101L, captor.getValue().getProductId());
        assertEquals(20, captor.getValue().getStatus());

        ErpSaleOrderSearchDataDTO data = (ErpSaleOrderSearchDataDTO) result.getData();
        ErpSaleOrderCapabilityDTO view = data.getList().get(0);
        assertEquals("XS-001", view.getNo());
        assertEquals("测试客户", view.getCustomerName());
    }

    @Test
    void createPurchaseOrder_shouldUseProductPurchasePriceAndSuggestAudit() {
        ErpProductDO product = new ErpProductDO();
        product.setId(101L);
        product.setUnitId(7L);
        product.setPurchasePrice(new BigDecimal("12.50"));
        when(productService.validProductList(List.of(101L))).thenReturn(List.of(product));
        when(purchaseOrderService.createPurchaseOrder(any())).thenReturn(31L);
        ErpPurchaseOrderDO purchaseOrder = new ErpPurchaseOrderDO();
        purchaseOrder.setId(31L);
        purchaseOrder.setNo("CG-001");
        purchaseOrder.setSupplierId(41L);
        purchaseOrder.setTotalCount(new BigDecimal("3"));
        purchaseOrder.setTotalPrice(new BigDecimal("37.50"));
        when(purchaseOrderService.getPurchaseOrder(31L)).thenReturn(purchaseOrder);
        when(purchaseOrderService.getPurchaseOrderItemListByOrderId(31L)).thenReturn(List.of());
        when(productService.getProductVOMap(any())).thenReturn(Map.of());

        ErpPurchaseOrderCreateReqDTO reqDTO = new ErpPurchaseOrderCreateReqDTO();
        reqDTO.setSupplierId(41L);
        ErpPurchaseOrderCreateReqDTO.Item item = new ErpPurchaseOrderCreateReqDTO.Item();
        item.setProductId(101L);
        item.setCount(new BigDecimal("3"));
        reqDTO.setItems(List.of(item));
        CapabilityResult result = provider.createPurchaseOrder(reqDTO);

        ArgumentCaptor<ErpPurchaseOrderSaveReqVO> captor = ArgumentCaptor.forClass(ErpPurchaseOrderSaveReqVO.class);
        verify(purchaseOrderService).createPurchaseOrder(captor.capture());
        ErpPurchaseOrderSaveReqVO.Item savedItem = captor.getValue().getItems().get(0);
        assertEquals(41L, captor.getValue().getSupplierId());
        assertNotNull(captor.getValue().getOrderTime());
        assertEquals(7L, savedItem.getProductUnitId());
        assertEquals(new BigDecimal("12.50"), savedItem.getProductPrice());
        assertEquals(new BigDecimal("3"), savedItem.getCount());

        assertTrue(result.isSuccess());
        ErpPurchaseOrderCapabilityDTO data = (ErpPurchaseOrderCapabilityDTO) result.getData();
        assertEquals("CG-001", data.getNo());
        assertEquals("erp.purchase.order.audit", result.getSuggestedNextActions().get(0).getName());
    }

    @Test
    void searchStockRecords_shouldDecorateProductWarehouseAndBizType() {
        ErpStockRecordDO record = new ErpStockRecordDO();
        record.setId(51L);
        record.setProductId(101L);
        record.setWarehouseId(1L);
        record.setCount(new BigDecimal("-2"));
        record.setTotalCount(new BigDecimal("198"));
        record.setBizType(ErpStockRecordBizTypeEnum.SALE_OUT.getType());
        record.setBizNo("XSC-001");
        record.setCreateTime(LocalDateTime.of(2026, 7, 30, 10, 0));
        when(stockRecordService.getStockRecordPage(any())).thenReturn(new PageResult<>(List.of(record), 1L));
        ErpProductRespVO product = new ErpProductRespVO();
        product.setId(101L);
        product.setName("A4 复印纸");
        when(productService.getProductVOMap(any())).thenReturn(Map.of(101L, product));
        ErpWarehouseDO warehouse = new ErpWarehouseDO();
        warehouse.setId(1L);
        warehouse.setName("测试主仓");
        when(warehouseService.getWarehouseMap(any())).thenReturn(Map.of(1L, warehouse));

        ErpStockRecordSearchReqDTO reqDTO = new ErpStockRecordSearchReqDTO();
        reqDTO.setProductId(101L);
        reqDTO.setWarehouseId(1L);
        reqDTO.setBizType(ErpStockRecordBizTypeEnum.SALE_OUT.getType());
        reqDTO.setBizNo("XSC");
        reqDTO.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        CapabilityResult result = provider.searchStockRecords(reqDTO);

        ErpStockRecordSearchDataDTO data = (ErpStockRecordSearchDataDTO) result.getData();
        assertEquals(1, data.getReturnedCount());
        assertEquals("A4 复印纸", data.getList().get(0).getProductName());
        assertEquals("测试主仓", data.getList().get(0).getWarehouseName());
        assertEquals("销售出库", data.getList().get(0).getBizTypeName());
        assertEquals("2026-07-30T10:00", data.getList().get(0).getCreateTime());
    }

    @Test
    void summarizeSale_shouldReturnDefaultAndCustomRanges() {
        when(saleStatisticsService.getSalePrice(nullable(LocalDateTime.class), nullable(LocalDateTime.class)))
                .thenReturn(new BigDecimal("100.00"));

        ErpStatisticsSummaryReqDTO reqDTO = new ErpStatisticsSummaryReqDTO();
        reqDTO.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        reqDTO.setEndTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        CapabilityResult result = provider.summarizeSale(reqDTO);

        ErpStatisticsSummaryDTO data = (ErpStatisticsSummaryDTO) result.getData();
        assertEquals(new BigDecimal("100.00"), data.getTodayPrice());
        assertEquals(new BigDecimal("100.00"), data.getCustomRangePrice());
        assertEquals("2026-07-01T00:00", data.getBeginTime());
        assertEquals("2026-08-01T00:00", data.getEndTime());
    }

    @Test
    void allCapabilityMethods_shouldReturnCapabilityResultAndDeclarePermissions() {
        int capabilityCount = 0;
        for (Method method : ErpCapabilityProvider.class.getDeclaredMethods()) {
            AgentCapability capability = method.getAnnotation(AgentCapability.class);
            if (capability == null) {
                continue;
            }
            capabilityCount++;
            assertEquals(CapabilityResult.class, method.getReturnType(),
                    method.getName() + " 必须返回 CapabilityResult");
            assertEquals(1, method.getParameterCount(),
                    method.getName() + " 必须只接收一个请求 DTO 参数");
            assertTrue(capability.permissions().length > 0,
                    capability.name() + " 必须声明权限标识");
            assertTrue(!capability.confirmationRequired(),
                    capability.name() + " 不应默认要求人工确认");
            if (capability.sideEffect()) {
                assertEquals(CapabilityRiskLevel.MEDIUM, capability.riskLevel(),
                        capability.name() + " 写操作必须显式声明中风险");
            }
        }
        assertEquals(17, capabilityCount);
    }

}
