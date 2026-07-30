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
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderAuditReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderCreateReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSaleOrderGetReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockQueryDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpStockQueryReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpSupplierSearchReqDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseCapabilityDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseSearchDataDTO;
import cn.iocoder.yudao.module.erp.capability.dto.ErpWarehouseSearchReqDTO;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.order.ErpSaleOrderSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.stock.ErpStockPageReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.warehouse.ErpWarehousePageReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.yudao.module.erp.enums.ErpAuditStatus;
import cn.iocoder.yudao.module.erp.service.product.ErpProductService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.yudao.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.yudao.module.erp.service.sale.ErpSaleOrderService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockService;
import cn.iocoder.yudao.module.erp.service.stock.ErpWarehouseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Function;
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
    private ErpSupplierService supplierService;

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
