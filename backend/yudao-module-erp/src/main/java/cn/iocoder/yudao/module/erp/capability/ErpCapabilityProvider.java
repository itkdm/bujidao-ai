package cn.iocoder.yudao.module.erp.capability;

import cn.iocoder.yudao.framework.acf.core.annotation.AgentCapability;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityRiskLevel;
import cn.iocoder.yudao.framework.acf.core.enums.CapabilityPermissionMode;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityEvidence;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityNextAction;
import cn.iocoder.yudao.framework.acf.core.model.CapabilityResult;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerCreateReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpCustomerSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpProductCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpProductSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpProductSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderAuditReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderCreateReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderGetReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpPurchaseOrderSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderAuditReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderCreateReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderGetReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStatisticsSummaryDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStatisticsSummaryReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockQueryDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockQueryReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockRecordCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockRecordSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockRecordSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseSearchReqDTO;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.order.ErpSaleOrderPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.order.ErpSaleOrderSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.record.ErpStockRecordPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.stock.ErpStockPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.warehouse.ErpWarehousePageReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockRecordDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.yudao.module.erp.enums.ErpAuditStatus;
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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ERP 能力提供者
 *
 * 通过调用既有 ERP Service 获取和写入数据，不修改原有业务实现。
 * 所有能力返回专用能力视图 DTO，不把 ERP 内部 DO 或管理后台 VO 直接暴露给 Agent。
 * 权限标识与管理后台 Controller 保持一致，避免出现「Agent 可见但人工不可见」的越权路径。
 *
 * @author bujidao
 */
@Service
public class ErpCapabilityProvider {

    /** 单次能力调用允许返回的最大条数，避免把整表数据塞进 Agent 上下文 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 未显式指定分页时使用的条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Resource
    private ErpProductService productService;
    @Resource
    private ErpStockService stockService;
    @Resource
    private ErpWarehouseService warehouseService;
    @Resource
    private ErpCustomerService customerService;
    @Resource
    private ErpSaleOrderService saleOrderService;
    @Resource
    private ErpPurchaseOrderService purchaseOrderService;
    @Resource
    private ErpSupplierService supplierService;
    @Resource
    private ErpStockRecordService stockRecordService;
    @Resource
    private ErpSaleStatisticsService saleStatisticsService;
    @Resource
    private ErpPurchaseStatisticsService purchaseStatisticsService;

    @AgentCapability(
            name = "erp.product.search",
            title = "ERP 商品查询",
            description = "按名称关键词、商品分类分页查询 ERP 商品，返回商品编号、条码、分类、单位和价格。"
                    + "拿到商品编号后可继续调用 erp.stock.query 查询库存。",
            category = "ERP",
            permissions = "erp:product:query",
            outputType = ErpProductSearchDataDTO.class)
    public CapabilityResult searchProducts(ErpProductSearchReqDTO reqDTO) {
        ErpProductPageReqVO pageReqVO = new ErpProductPageReqVO();
        pageReqVO.setName(reqDTO.getKeyword());
        pageReqVO.setCategoryId(reqDTO.getCategoryId());
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpProductRespVO> pageResult = productService.getProductVOPage(pageReqVO);

        List<ErpProductCapabilityDTO> products = pageResult.getList().stream()
                .map(this::toProductView)
                .toList();
        ErpProductSearchDataDTO data = ErpProductSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(products.size())
                .list(products)
                .build();
        CapabilityResult result = CapabilityResult.success(data, "已返回商品候选列表");
        if (products.isEmpty()) {
            return result;
        }
        return result.withSuggestedNextAction(CapabilityNextAction.of("erp.stock.query", "查询商品库存",
                Map.of("productId", products.get(0).getId())));
    }

    @AgentCapability(
            name = "erp.stock.query",
            title = "ERP 库存查询",
            description = "查询指定商品的库存合计数量与分仓明细。数量为 ERP 库存台账的当前值，"
                    + "不扣减未出库的销售订单占用。",
            category = "ERP",
            // 返回结果包含仓库名称，因此同时要求库存和仓库两个查询权限
            permissions = {"erp:stock:query", "erp:warehouse:query"},
            permissionMode = CapabilityPermissionMode.ALL,
            outputType = ErpStockQueryDataDTO.class)
    public CapabilityResult queryStock(ErpStockQueryReqDTO reqDTO) {
        ErpStockPageReqVO pageReqVO = new ErpStockPageReqVO();
        pageReqVO.setProductId(reqDTO.getProductId());
        pageReqVO.setWarehouseId(reqDTO.getWarehouseId());
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(MAX_PAGE_SIZE);
        PageResult<ErpStockDO> pageResult = stockService.getStockPage(pageReqVO);

        Map<Long, ErpWarehouseDO> warehouseMap = warehouseService.getWarehouseMap(
                pageResult.getList().stream().map(ErpStockDO::getWarehouseId).toList());
        List<ErpStockQueryDataDTO.WarehouseStock> warehouseStocks = pageResult.getList().stream()
                .map(stock -> ErpStockQueryDataDTO.WarehouseStock.builder()
                        .warehouseId(stock.getWarehouseId())
                        .warehouseName(warehouseName(warehouseMap.get(stock.getWarehouseId())))
                        .count(nullToZero(stock.getCount()))
                        .build())
                .toList();
        ErpStockQueryDataDTO data = ErpStockQueryDataDTO.builder()
                .productId(reqDTO.getProductId())
                .warehouseId(reqDTO.getWarehouseId())
                // 指定仓库时只汇总该仓库，避免返回的合计数量与明细互相矛盾
                .totalCount(reqDTO.getWarehouseId() == null
                        ? nullToZero(stockService.getStockCount(reqDTO.getProductId()))
                        : sumCount(warehouseStocks))
                .warehouseStocks(warehouseStocks)
                .build();
        return CapabilityResult.success(data, "已返回库存快照")
                .withEvidence(CapabilityEvidence.of("stock_snapshot", "库存来自 ERP 当前库存台账",
                        Map.of("warehouseCount", warehouseStocks.size(),
                                "scope", reqDTO.getWarehouseId() == null ? "ALL_WAREHOUSE" : "SINGLE_WAREHOUSE")));
    }

    @AgentCapability(
            name = "erp.warehouse.search",
            title = "ERP 仓库查询",
            description = "按名称关键词、开启状态分页查询 ERP 仓库，用于在库存查询和调拨沟通前确认仓库编号。",
            category = "ERP",
            permissions = "erp:warehouse:query",
            outputType = ErpWarehouseSearchDataDTO.class)
    public CapabilityResult searchWarehouses(ErpWarehouseSearchReqDTO reqDTO) {
        ErpWarehousePageReqVO pageReqVO = new ErpWarehousePageReqVO();
        pageReqVO.setName(reqDTO.getKeyword());
        pageReqVO.setStatus(reqDTO.getStatus());
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpWarehouseDO> pageResult = warehouseService.getWarehousePage(pageReqVO);

        List<ErpWarehouseCapabilityDTO> warehouses = pageResult.getList().stream()
                .map(this::toWarehouseView)
                .toList();
        ErpWarehouseSearchDataDTO data = ErpWarehouseSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(warehouses.size())
                .list(warehouses)
                .build();
        return CapabilityResult.success(data, "已返回仓库列表");
    }

    @AgentCapability(
            name = "erp.customer.search",
            title = "ERP 客户查询",
            description = "按客户名称关键词、手机号分页查询 ERP 客户候选列表。"
                    + "返回结果不包含银行账号、开户行、纳税识别号等财务敏感信息。",
            category = "ERP",
            permissions = "erp:customer:query",
            outputType = ErpCustomerSearchDataDTO.class)
    public CapabilityResult searchCustomers(ErpCustomerSearchReqDTO reqDTO) {
        ErpCustomerPageReqVO pageReqVO = new ErpCustomerPageReqVO();
        pageReqVO.setName(reqDTO.getKeyword());
        pageReqVO.setMobile(reqDTO.getMobile());
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpCustomerDO> pageResult = customerService.getCustomerPage(pageReqVO);

        List<ErpCustomerCapabilityDTO> customers = pageResult.getList().stream()
                .map(this::toCustomerView)
                .toList();
        ErpCustomerSearchDataDTO data = ErpCustomerSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(customers.size())
                .list(customers)
                .build();
        return CapabilityResult.success(data, "已返回客户候选列表");
    }

    @AgentCapability(
            name = "erp.customer.create",
            title = "ERP 客户创建",
            description = "创建 ERP 客户基础资料。只写入客户名称、联系人、电话和备注等基础字段，"
                    + "不接收银行账号、开户行、纳税识别号等财务敏感字段。",
            category = "ERP",
            permissions = "erp:customer:create",
            riskLevel = CapabilityRiskLevel.MEDIUM,
            sideEffect = true,
            outputType = ErpCustomerCapabilityDTO.class)
    public CapabilityResult createCustomer(ErpCustomerCreateReqDTO reqDTO) {
        ErpCustomerSaveReqVO createReqVO = new ErpCustomerSaveReqVO();
        createReqVO.setName(reqDTO.getName());
        createReqVO.setContact(reqDTO.getContact());
        createReqVO.setMobile(reqDTO.getMobile());
        createReqVO.setTelephone(reqDTO.getTelephone());
        createReqVO.setRemark(reqDTO.getRemark());
        createReqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        createReqVO.setSort(0);
        Long customerId = customerService.createCustomer(createReqVO);

        ErpCustomerCapabilityDTO customer = toCustomerView(customerService.getCustomer(customerId));
        return CapabilityResult.success(customer, "已创建客户")
                .withSuggestedNextAction(CapabilityNextAction.of("erp.sale.order.create", "为该客户创建销售订单",
                        Map.of("customerId", customerId)));
    }

    @AgentCapability(
            name = "erp.sale.order.create",
            title = "ERP 销售订单创建",
            description = "为指定客户创建销售订单草稿，复用 ERP 原有商品、客户、价格和税额校验计算逻辑。"
                    + "创建后订单处于未审核状态，可继续调用 erp.sale.order.audit 审核。",
            category = "ERP",
            permissions = "erp:sale-order:create",
            riskLevel = CapabilityRiskLevel.MEDIUM,
            sideEffect = true,
            outputType = ErpSaleOrderCapabilityDTO.class)
    public CapabilityResult createSaleOrder(ErpSaleOrderCreateReqDTO reqDTO) {
        ErpSaleOrderSaveReqVO createReqVO = new ErpSaleOrderSaveReqVO();
        createReqVO.setCustomerId(reqDTO.getCustomerId());
        createReqVO.setOrderTime(reqDTO.getOrderTime() == null ? LocalDateTime.now() : reqDTO.getOrderTime());
        createReqVO.setDiscountPercent(nullToZero(reqDTO.getDiscountPercent()));
        createReqVO.setDepositPrice(nullToZero(reqDTO.getDepositPrice()));
        createReqVO.setRemark(reqDTO.getRemark());
        createReqVO.setItems(buildSaleOrderItems(reqDTO.getItems()));
        Long saleOrderId = saleOrderService.createSaleOrder(createReqVO);

        ErpSaleOrderCapabilityDTO saleOrder = toSaleOrderView(saleOrderService.getSaleOrder(saleOrderId));
        return CapabilityResult.success(saleOrder, "已创建销售订单")
                .withSuggestedNextAction(CapabilityNextAction.of("erp.sale.order.audit", "审核该销售订单",
                        Map.of("id", saleOrderId, "approved", true)));
    }

    @AgentCapability(
            name = "erp.sale.order.search",
            title = "ERP 销售订单列表查询",
            description = "按销售订单号、客户、商品、审核状态分页查询销售订单，用于跟进客户订单和核对订单状态。",
            category = "ERP",
            permissions = "erp:sale-order:query",
            outputType = ErpSaleOrderSearchDataDTO.class)
    public CapabilityResult searchSaleOrders(ErpSaleOrderSearchReqDTO reqDTO) {
        ErpSaleOrderPageReqVO pageReqVO = new ErpSaleOrderPageReqVO();
        pageReqVO.setNo(reqDTO.getNo());
        pageReqVO.setCustomerId(reqDTO.getCustomerId());
        pageReqVO.setProductId(reqDTO.getProductId());
        pageReqVO.setStatus(reqDTO.getStatus());
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpSaleOrderDO> pageResult = saleOrderService.getSaleOrderPage(pageReqVO);

        Map<Long, ErpCustomerDO> customerMap = customerService.getCustomerMap(
                pageResult.getList().stream().map(ErpSaleOrderDO::getCustomerId).toList());
        List<ErpSaleOrderCapabilityDTO> saleOrders = pageResult.getList().stream()
                .map(saleOrder -> toSaleOrderView(saleOrder, customerName(customerMap.get(saleOrder.getCustomerId()))))
                .toList();
        ErpSaleOrderSearchDataDTO data = ErpSaleOrderSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(saleOrders.size())
                .list(saleOrders)
                .build();
        return CapabilityResult.success(data, "已返回销售订单列表");
    }

    @AgentCapability(
            name = "erp.sale.order.get",
            title = "ERP 销售订单查询",
            description = "按销售订单编号查询订单主体和明细，用于确认订单状态、商品、数量和金额。",
            category = "ERP",
            permissions = "erp:sale-order:query",
            outputType = ErpSaleOrderCapabilityDTO.class)
    public CapabilityResult getSaleOrder(ErpSaleOrderGetReqDTO reqDTO) {
        ErpSaleOrderDO saleOrder = saleOrderService.getSaleOrder(reqDTO.getId());
        return CapabilityResult.success(toSaleOrderView(saleOrder), "已返回销售订单详情");
    }

    @AgentCapability(
            name = "erp.sale.order.audit",
            title = "ERP 销售订单审核",
            description = "审核或反审核销售订单。审核订单不会直接扣减库存，真实库存变化仍由 ERP 销售出库流程负责。",
            category = "ERP",
            permissions = "erp:sale-order:update-status",
            riskLevel = CapabilityRiskLevel.MEDIUM,
            sideEffect = true,
            outputType = ErpSaleOrderCapabilityDTO.class)
    public CapabilityResult auditSaleOrder(ErpSaleOrderAuditReqDTO reqDTO) {
        Integer status = Boolean.TRUE.equals(reqDTO.getApproved())
                ? ErpAuditStatus.APPROVE.getStatus() : ErpAuditStatus.PROCESS.getStatus();
        saleOrderService.updateSaleOrderStatus(reqDTO.getId(), status);
        return CapabilityResult.success(toSaleOrderView(saleOrderService.getSaleOrder(reqDTO.getId())),
                Boolean.TRUE.equals(reqDTO.getApproved()) ? "已审核销售订单" : "已反审核销售订单");
    }

    @AgentCapability(
            name = "erp.purchase.order.search",
            title = "ERP 采购订单列表查询",
            description = "按采购订单号、供应商、商品、审核状态分页查询采购订单，用于补货跟进和供应商订单核对。",
            category = "ERP",
            permissions = "erp:purchase-order:query",
            outputType = ErpPurchaseOrderSearchDataDTO.class)
    public CapabilityResult searchPurchaseOrders(ErpPurchaseOrderSearchReqDTO reqDTO) {
        ErpPurchaseOrderPageReqVO pageReqVO = new ErpPurchaseOrderPageReqVO();
        pageReqVO.setNo(reqDTO.getNo());
        pageReqVO.setSupplierId(reqDTO.getSupplierId());
        pageReqVO.setProductId(reqDTO.getProductId());
        pageReqVO.setStatus(reqDTO.getStatus());
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpPurchaseOrderDO> pageResult = purchaseOrderService.getPurchaseOrderPage(pageReqVO);

        Map<Long, ErpSupplierDO> supplierMap = supplierService.getSupplierMap(
                pageResult.getList().stream().map(ErpPurchaseOrderDO::getSupplierId).toList());
        List<ErpPurchaseOrderCapabilityDTO> purchaseOrders = pageResult.getList().stream()
                .map(order -> toPurchaseOrderView(order, supplierName(supplierMap.get(order.getSupplierId()))))
                .toList();
        ErpPurchaseOrderSearchDataDTO data = ErpPurchaseOrderSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(purchaseOrders.size())
                .list(purchaseOrders)
                .build();
        return CapabilityResult.success(data, "已返回采购订单列表");
    }

    @AgentCapability(
            name = "erp.purchase.order.create",
            title = "ERP 采购订单创建",
            description = "为指定供应商创建采购订单草稿，复用 ERP 原有商品、供应商、价格和税额校验计算逻辑。"
                    + "创建后订单处于未审核状态，可继续调用 erp.purchase.order.audit 审核。",
            category = "ERP",
            permissions = "erp:purchase-order:create",
            riskLevel = CapabilityRiskLevel.MEDIUM,
            sideEffect = true,
            outputType = ErpPurchaseOrderCapabilityDTO.class)
    public CapabilityResult createPurchaseOrder(ErpPurchaseOrderCreateReqDTO reqDTO) {
        ErpPurchaseOrderSaveReqVO createReqVO = new ErpPurchaseOrderSaveReqVO();
        createReqVO.setSupplierId(reqDTO.getSupplierId());
        createReqVO.setOrderTime(reqDTO.getOrderTime() == null ? LocalDateTime.now() : reqDTO.getOrderTime());
        createReqVO.setDiscountPercent(nullToZero(reqDTO.getDiscountPercent()));
        createReqVO.setDepositPrice(nullToZero(reqDTO.getDepositPrice()));
        createReqVO.setRemark(reqDTO.getRemark());
        createReqVO.setItems(buildPurchaseOrderItems(reqDTO.getItems()));
        Long purchaseOrderId = purchaseOrderService.createPurchaseOrder(createReqVO);

        ErpPurchaseOrderCapabilityDTO purchaseOrder = toPurchaseOrderView(
                purchaseOrderService.getPurchaseOrder(purchaseOrderId));
        return CapabilityResult.success(purchaseOrder, "已创建采购订单")
                .withSuggestedNextAction(CapabilityNextAction.of("erp.purchase.order.audit", "审核该采购订单",
                        Map.of("id", purchaseOrderId, "approved", true)));
    }

    @AgentCapability(
            name = "erp.purchase.order.get",
            title = "ERP 采购订单查询",
            description = "按采购订单编号查询订单主体和明细，用于确认采购订单状态、商品、数量和金额。",
            category = "ERP",
            permissions = "erp:purchase-order:query",
            outputType = ErpPurchaseOrderCapabilityDTO.class)
    public CapabilityResult getPurchaseOrder(ErpPurchaseOrderGetReqDTO reqDTO) {
        ErpPurchaseOrderDO purchaseOrder = purchaseOrderService.getPurchaseOrder(reqDTO.getId());
        return CapabilityResult.success(toPurchaseOrderView(purchaseOrder), "已返回采购订单详情");
    }

    @AgentCapability(
            name = "erp.purchase.order.audit",
            title = "ERP 采购订单审核",
            description = "审核或反审核采购订单。审核订单不会直接增加库存，真实库存变化仍由 ERP 采购入库流程负责。",
            category = "ERP",
            permissions = "erp:purchase-order:update-status",
            riskLevel = CapabilityRiskLevel.MEDIUM,
            sideEffect = true,
            outputType = ErpPurchaseOrderCapabilityDTO.class)
    public CapabilityResult auditPurchaseOrder(ErpPurchaseOrderAuditReqDTO reqDTO) {
        Integer status = Boolean.TRUE.equals(reqDTO.getApproved())
                ? ErpAuditStatus.APPROVE.getStatus() : ErpAuditStatus.PROCESS.getStatus();
        purchaseOrderService.updatePurchaseOrderStatus(reqDTO.getId(), status);
        return CapabilityResult.success(toPurchaseOrderView(purchaseOrderService.getPurchaseOrder(reqDTO.getId())),
                Boolean.TRUE.equals(reqDTO.getApproved()) ? "已审核采购订单" : "已反审核采购订单");
    }

    @AgentCapability(
            name = "erp.supplier.search",
            title = "ERP 供应商查询",
            description = "按供应商名称关键词、手机号分页查询 ERP 供应商候选列表。"
                    + "返回结果不包含银行账号、开户行、纳税识别号等财务敏感信息。",
            category = "ERP",
            permissions = "erp:supplier:query",
            outputType = ErpSupplierSearchDataDTO.class)
    public CapabilityResult searchSuppliers(ErpSupplierSearchReqDTO reqDTO) {
        ErpSupplierPageReqVO pageReqVO = new ErpSupplierPageReqVO();
        pageReqVO.setName(reqDTO.getKeyword());
        pageReqVO.setMobile(reqDTO.getMobile());
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpSupplierDO> pageResult = supplierService.getSupplierPage(pageReqVO);

        List<ErpSupplierCapabilityDTO> suppliers = pageResult.getList().stream()
                .map(this::toSupplierView)
                .toList();
        ErpSupplierSearchDataDTO data = ErpSupplierSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(suppliers.size())
                .list(suppliers)
                .build();
        return CapabilityResult.success(data, "已返回供应商候选列表");
    }

    @AgentCapability(
            name = "erp.stock.record.search",
            title = "ERP 库存流水查询",
            description = "按商品、仓库、业务类型、业务单号和时间范围分页查询库存流水，用于解释库存变化来源。",
            category = "ERP",
            permissions = {"erp:stock-record:query", "erp:product:query", "erp:warehouse:query"},
            permissionMode = CapabilityPermissionMode.ALL,
            outputType = ErpStockRecordSearchDataDTO.class)
    public CapabilityResult searchStockRecords(ErpStockRecordSearchReqDTO reqDTO) {
        ErpStockRecordPageReqVO pageReqVO = new ErpStockRecordPageReqVO();
        pageReqVO.setProductId(reqDTO.getProductId());
        pageReqVO.setWarehouseId(reqDTO.getWarehouseId());
        pageReqVO.setBizType(reqDTO.getBizType());
        pageReqVO.setBizNo(reqDTO.getBizNo());
        pageReqVO.setCreateTime(toRange(reqDTO.getBeginTime(), reqDTO.getEndTime()));
        pageReqVO.setPageNo(normalizePageNo(reqDTO.getPageNo()));
        pageReqVO.setPageSize(normalizePageSize(reqDTO.getPageSize()));
        PageResult<ErpStockRecordDO> pageResult = stockRecordService.getStockRecordPage(pageReqVO);

        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                pageResult.getList().stream().map(ErpStockRecordDO::getProductId).distinct().toList());
        Map<Long, ErpWarehouseDO> warehouseMap = warehouseService.getWarehouseMap(
                pageResult.getList().stream().map(ErpStockRecordDO::getWarehouseId).distinct().toList());
        List<ErpStockRecordCapabilityDTO> stockRecords = pageResult.getList().stream()
                .map(record -> toStockRecordView(record, productMap.get(record.getProductId()),
                        warehouseMap.get(record.getWarehouseId())))
                .toList();
        ErpStockRecordSearchDataDTO data = ErpStockRecordSearchDataDTO.builder()
                .total(pageResult.getTotal())
                .returnedCount(stockRecords.size())
                .list(stockRecords)
                .build();
        return CapabilityResult.success(data, "已返回库存流水列表")
                .withEvidence(CapabilityEvidence.of("stock_record", "库存流水来自 ERP 产品库存明细",
                        Map.of("returnedCount", stockRecords.size())));
    }

    @AgentCapability(
            name = "erp.statistics.sale.summary",
            title = "ERP 销售金额统计",
            description = "查询今日、昨日、本月、本年销售金额，并可按自定义时间段统计销售金额。",
            category = "ERP",
            permissions = "erp:statistics:query",
            outputType = ErpStatisticsSummaryDTO.class)
    public CapabilityResult summarizeSale(ErpStatisticsSummaryReqDTO reqDTO) {
        return CapabilityResult.success(buildStatisticsSummary(reqDTO, saleStatisticsService::getSalePrice),
                "已返回销售金额统计");
    }

    @AgentCapability(
            name = "erp.statistics.purchase.summary",
            title = "ERP 采购金额统计",
            description = "查询今日、昨日、本月、本年采购金额，并可按自定义时间段统计采购金额。",
            category = "ERP",
            permissions = "erp:statistics:query",
            outputType = ErpStatisticsSummaryDTO.class)
    public CapabilityResult summarizePurchase(ErpStatisticsSummaryReqDTO reqDTO) {
        return CapabilityResult.success(buildStatisticsSummary(reqDTO, purchaseStatisticsService::getPurchasePrice),
                "已返回采购金额统计");
    }

    private ErpProductCapabilityDTO toProductView(ErpProductRespVO product) {
        return ErpProductCapabilityDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .barCode(product.getBarCode())
                .categoryId(product.getCategoryId())
                .categoryName(product.getCategoryName())
                .unitName(product.getUnitName())
                .status(product.getStatus())
                .standard(product.getStandard())
                .purchasePrice(product.getPurchasePrice())
                .salePrice(product.getSalePrice())
                .build();
    }

    private ErpWarehouseCapabilityDTO toWarehouseView(ErpWarehouseDO warehouse) {
        return ErpWarehouseCapabilityDTO.builder()
                .id(warehouse.getId())
                .name(warehouse.getName())
                .address(warehouse.getAddress())
                .principal(warehouse.getPrincipal())
                .status(warehouse.getStatus())
                .defaultStatus(warehouse.getDefaultStatus())
                .build();
    }

    private ErpCustomerCapabilityDTO toCustomerView(ErpCustomerDO customer) {
        return ErpCustomerCapabilityDTO.builder()
                .id(customer.getId())
                .name(customer.getName())
                .contact(customer.getContact())
                .mobile(customer.getMobile())
                .telephone(customer.getTelephone())
                .status(customer.getStatus())
                .build();
    }

    private ErpSupplierCapabilityDTO toSupplierView(ErpSupplierDO supplier) {
        return ErpSupplierCapabilityDTO.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .contact(supplier.getContact())
                .mobile(supplier.getMobile())
                .telephone(supplier.getTelephone())
                .status(supplier.getStatus())
                .build();
    }

    private List<ErpSaleOrderSaveReqVO.Item> buildSaleOrderItems(List<ErpSaleOrderCreateReqDTO.Item> items) {
        List<Long> productIds = items.stream()
                .map(ErpSaleOrderCreateReqDTO.Item::getProductId)
                .distinct()
                .toList();
        Map<Long, ErpProductDO> productMap = productService.validProductList(productIds).stream()
                .collect(Collectors.toMap(ErpProductDO::getId, Function.identity()));
        return items.stream()
                .map(item -> toSaleOrderItemReq(item, productMap.get(item.getProductId())))
                .toList();
    }

    private ErpSaleOrderSaveReqVO.Item toSaleOrderItemReq(ErpSaleOrderCreateReqDTO.Item item, ErpProductDO product) {
        ErpSaleOrderSaveReqVO.Item itemReqVO = new ErpSaleOrderSaveReqVO.Item();
        itemReqVO.setProductId(item.getProductId());
        itemReqVO.setProductUnitId(product.getUnitId());
        itemReqVO.setProductPrice(defaultValue(item.getProductPrice(), product.getSalePrice()));
        itemReqVO.setCount(item.getCount());
        itemReqVO.setTaxPercent(nullToZero(item.getTaxPercent()));
        itemReqVO.setRemark(item.getRemark());
        return itemReqVO;
    }

    private ErpSaleOrderCapabilityDTO toSaleOrderView(ErpSaleOrderDO saleOrder) {
        return toSaleOrderView(saleOrder, null);
    }

    private ErpSaleOrderCapabilityDTO toSaleOrderView(ErpSaleOrderDO saleOrder, String customerName) {
        List<ErpSaleOrderItemDO> orderItems = saleOrderService.getSaleOrderItemListByOrderId(saleOrder.getId());
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(orderItems.stream()
                .map(ErpSaleOrderItemDO::getProductId)
                .distinct()
                .toList());
        return ErpSaleOrderCapabilityDTO.builder()
                .id(saleOrder.getId())
                .no(saleOrder.getNo())
                .status(saleOrder.getStatus())
                .statusName(auditStatusName(saleOrder.getStatus()))
                .customerId(saleOrder.getCustomerId())
                .customerName(customerName)
                .orderTime(saleOrder.getOrderTime() == null ? null : saleOrder.getOrderTime().toString())
                .totalCount(nullToZero(saleOrder.getTotalCount()))
                .totalPrice(nullToZero(saleOrder.getTotalPrice()))
                .remark(saleOrder.getRemark())
                .items(orderItems.stream()
                        .map(item -> toSaleOrderItemView(item, productMap.get(item.getProductId())))
                        .toList())
                .build();
    }

    private ErpSaleOrderCapabilityDTO.Item toSaleOrderItemView(ErpSaleOrderItemDO item, ErpProductRespVO product) {
        return ErpSaleOrderCapabilityDTO.Item.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(product == null ? null : product.getName())
                .unitName(product == null ? null : product.getUnitName())
                .productPrice(nullToZero(item.getProductPrice()))
                .count(nullToZero(item.getCount()))
                .totalPrice(nullToZero(item.getTotalPrice()))
                .taxPercent(nullToZero(item.getTaxPercent()))
                .remark(item.getRemark())
                .build();
    }

    private List<ErpPurchaseOrderSaveReqVO.Item> buildPurchaseOrderItems(List<ErpPurchaseOrderCreateReqDTO.Item> items) {
        List<Long> productIds = items.stream()
                .map(ErpPurchaseOrderCreateReqDTO.Item::getProductId)
                .distinct()
                .toList();
        Map<Long, ErpProductDO> productMap = productService.validProductList(productIds).stream()
                .collect(Collectors.toMap(ErpProductDO::getId, Function.identity()));
        return items.stream()
                .map(item -> toPurchaseOrderItemReq(item, productMap.get(item.getProductId())))
                .toList();
    }

    private ErpPurchaseOrderSaveReqVO.Item toPurchaseOrderItemReq(ErpPurchaseOrderCreateReqDTO.Item item,
                                                                  ErpProductDO product) {
        ErpPurchaseOrderSaveReqVO.Item itemReqVO = new ErpPurchaseOrderSaveReqVO.Item();
        itemReqVO.setProductId(item.getProductId());
        itemReqVO.setProductUnitId(product.getUnitId());
        itemReqVO.setProductPrice(defaultValue(item.getProductPrice(), product.getPurchasePrice()));
        itemReqVO.setCount(item.getCount());
        itemReqVO.setTaxPercent(nullToZero(item.getTaxPercent()));
        itemReqVO.setRemark(item.getRemark());
        return itemReqVO;
    }

    private ErpPurchaseOrderCapabilityDTO toPurchaseOrderView(ErpPurchaseOrderDO purchaseOrder) {
        return toPurchaseOrderView(purchaseOrder, null);
    }

    private ErpPurchaseOrderCapabilityDTO toPurchaseOrderView(ErpPurchaseOrderDO purchaseOrder, String supplierName) {
        List<ErpPurchaseOrderItemDO> orderItems = purchaseOrderService
                .getPurchaseOrderItemListByOrderId(purchaseOrder.getId());
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(orderItems.stream()
                .map(ErpPurchaseOrderItemDO::getProductId)
                .distinct()
                .toList());
        return ErpPurchaseOrderCapabilityDTO.builder()
                .id(purchaseOrder.getId())
                .no(purchaseOrder.getNo())
                .status(purchaseOrder.getStatus())
                .statusName(auditStatusName(purchaseOrder.getStatus()))
                .supplierId(purchaseOrder.getSupplierId())
                .supplierName(supplierName)
                .orderTime(purchaseOrder.getOrderTime() == null ? null : purchaseOrder.getOrderTime().toString())
                .totalCount(nullToZero(purchaseOrder.getTotalCount()))
                .totalPrice(nullToZero(purchaseOrder.getTotalPrice()))
                .inCount(nullToZero(purchaseOrder.getInCount()))
                .returnCount(nullToZero(purchaseOrder.getReturnCount()))
                .remark(purchaseOrder.getRemark())
                .items(orderItems.stream()
                        .map(item -> toPurchaseOrderItemView(item, productMap.get(item.getProductId())))
                        .toList())
                .build();
    }

    private ErpPurchaseOrderCapabilityDTO.Item toPurchaseOrderItemView(ErpPurchaseOrderItemDO item,
                                                                       ErpProductRespVO product) {
        return ErpPurchaseOrderCapabilityDTO.Item.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(product == null ? null : product.getName())
                .unitName(product == null ? null : product.getUnitName())
                .productPrice(nullToZero(item.getProductPrice()))
                .count(nullToZero(item.getCount()))
                .totalPrice(nullToZero(item.getTotalPrice()))
                .taxPercent(nullToZero(item.getTaxPercent()))
                .inCount(nullToZero(item.getInCount()))
                .returnCount(nullToZero(item.getReturnCount()))
                .remark(item.getRemark())
                .build();
    }

    private ErpStockRecordCapabilityDTO toStockRecordView(ErpStockRecordDO record, ErpProductRespVO product,
                                                          ErpWarehouseDO warehouse) {
        return ErpStockRecordCapabilityDTO.builder()
                .id(record.getId())
                .productId(record.getProductId())
                .productName(product == null ? null : product.getName())
                .warehouseId(record.getWarehouseId())
                .warehouseName(warehouseName(warehouse))
                .count(nullToZero(record.getCount()))
                .totalCount(nullToZero(record.getTotalCount()))
                .bizType(record.getBizType())
                .bizTypeName(stockRecordBizTypeName(record.getBizType()))
                .bizNo(record.getBizNo())
                .createTime(record.getCreateTime() == null ? null : record.getCreateTime().toString())
                .build();
    }

    private ErpStatisticsSummaryDTO buildStatisticsSummary(ErpStatisticsSummaryReqDTO reqDTO,
                                                           BiFunction<LocalDateTime, LocalDateTime, BigDecimal> loader) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);
        LocalDateTime month = today.withDayOfMonth(1);
        LocalDateTime year = today.withDayOfYear(1);
        return ErpStatisticsSummaryDTO.builder()
                .todayPrice(nullToZero(loader.apply(today, null)))
                .yesterdayPrice(nullToZero(loader.apply(yesterday, today)))
                .monthPrice(nullToZero(loader.apply(month, null)))
                .yearPrice(nullToZero(loader.apply(year, null)))
                .beginTime(reqDTO.getBeginTime() == null ? null : reqDTO.getBeginTime().toString())
                .endTime(reqDTO.getEndTime() == null ? null : reqDTO.getEndTime().toString())
                .customRangePrice(reqDTO.getBeginTime() == null ? null
                        : nullToZero(loader.apply(reqDTO.getBeginTime(), reqDTO.getEndTime())))
                .build();
    }

    private LocalDateTime[] toRange(LocalDateTime beginTime, LocalDateTime endTime) {
        if (beginTime == null && endTime == null) {
            return null;
        }
        return new LocalDateTime[]{beginTime, endTime};
    }

    private String customerName(ErpCustomerDO customer) {
        return customer == null ? null : customer.getName();
    }

    private String supplierName(ErpSupplierDO supplier) {
        return supplier == null ? null : supplier.getName();
    }

    private String stockRecordBizTypeName(Integer bizType) {
        for (ErpStockRecordBizTypeEnum typeEnum : ErpStockRecordBizTypeEnum.values()) {
            if (typeEnum.getType().equals(bizType)) {
                return typeEnum.getName();
            }
        }
        return null;
    }

    private String auditStatusName(Integer status) {
        for (ErpAuditStatus auditStatus : ErpAuditStatus.values()) {
            if (auditStatus.getStatus().equals(status)) {
                return auditStatus.getName();
            }
        }
        return null;
    }

    private String warehouseName(ErpWarehouseDO warehouse) {
        return warehouse == null ? null : warehouse.getName();
    }

    private BigDecimal sumCount(List<ErpStockQueryDataDTO.WarehouseStock> warehouseStocks) {
        return warehouseStocks.stream()
                .map(ErpStockQueryDataDTO.WarehouseStock::getCount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal defaultValue(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? nullToZero(defaultValue) : value;
    }

    private Integer normalizePageNo(Integer pageNo) {
        return pageNo == null ? 1 : Math.max(1, pageNo);
    }

    private Integer normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
    }

}
