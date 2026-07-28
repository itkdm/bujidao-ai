/*
 ERP ACF 能力联调测试数据（MySQL）

 用途：为 erp.product.search / erp.stock.query / erp.warehouse.search /
       erp.customer.search / erp.supplier.search 五个只读能力准备可查的数据。

 说明：
 1. 前置条件：先执行同目录 erp.sql 建表。
 2. 全部使用固定主键，并且先 DELETE 再 INSERT，可以重复执行。
 3. 只写入 tenant_id = 1（芋道源码，默认租户）的数据。如果你用其他租户登录，
    需要把下面的 @tenant_id 改成对应的租户编号后重新执行。
 4. 手机号、电话均为测试用虚构号码，不是真实联系人信息。
*/

SET NAMES utf8mb4;

SET @tenant_id := 1;
SET @creator := '1';

-- ----------------------------
-- 产品分类
-- ----------------------------
DELETE FROM `erp_product_category` WHERE `id` IN (9001, 9002, 9003);
INSERT INTO `erp_product_category` (`id`, `parent_id`, `name`, `code`, `sort`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, 0, '办公用品', 'ACF-OFFICE', 1, 0, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, 9001, '文具', 'ACF-OFFICE-PEN', 1, 0, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, 0, '电子设备', 'ACF-DEVICE', 2, 0, @creator, NOW(), @creator, NOW(), b'0', @tenant_id);

-- ----------------------------
-- 产品单位
-- ----------------------------
DELETE FROM `erp_product_unit` WHERE `id` IN (9001, 9002, 9003);
INSERT INTO `erp_product_unit` (`id`, `name`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, '个', 0, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, '盒', 0, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, '台', 0, @creator, NOW(), @creator, NOW(), b'0', @tenant_id);

-- ----------------------------
-- 产品
-- 覆盖场景：名称模糊命中多条、不同分类、开启与停用状态、无库存产品
-- ----------------------------
DELETE FROM `erp_product` WHERE `id` BETWEEN 9001 AND 9006;
INSERT INTO `erp_product` (`id`, `name`, `bar_code`, `category_id`, `unit_id`, `status`, `standard`, `remark`, `expiry_day`, `weight`, `purchase_price`, `sale_price`, `min_price`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, '测试中性笔-黑色', 'ACF-PEN-BLACK', 9002, 9001, 0, '0.5mm', 'ACF 联调用', NULL, 0.010000, 1.200000, 3.000000, 2.500000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, '测试中性笔-红色', 'ACF-PEN-RED', 9002, 9001, 0, '0.5mm', 'ACF 联调用', NULL, 0.010000, 1.200000, 3.000000, 2.500000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, '测试A4复印纸', 'ACF-PAPER-A4', 9001, 9002, 0, '70g 500张/包', 'ACF 联调用', NULL, 2.500000, 18.000000, 26.000000, 22.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9004, '测试无线鼠标', 'ACF-MOUSE-01', 9003, 9003, 0, '2.4G', 'ACF 联调用', NULL, 0.080000, 35.000000, 69.000000, 55.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9005, '测试机械键盘（已停用）', 'ACF-KEYBOARD-01', 9003, 9003, 1, '87 键', '停用状态，用于验证能力不过滤状态', NULL, 0.900000, 180.000000, 299.000000, 260.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9006, '测试零库存显示器', 'ACF-MONITOR-01', 9003, 9003, 0, '27 英寸', '故意不建库存记录，用于验证空库存返回', NULL, 4.500000, 700.000000, 1099.000000, 950.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id);

-- ----------------------------
-- 仓库
-- 覆盖场景：默认仓库、多仓库、停用仓库
-- ----------------------------
DELETE FROM `erp_warehouse` WHERE `id` IN (9001, 9002, 9003);
INSERT INTO `erp_warehouse` (`id`, `name`, `address`, `sort`, `remark`, `principal`, `warehouse_price`, `truckage_price`, `status`, `default_status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, '测试主仓', '上海市浦东新区测试路 1 号', 1, 'ACF 联调用', '张测试', 1.000000, 0.500000, 0, b'1', @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, '测试备用仓', '上海市闵行区测试路 2 号', 2, 'ACF 联调用', '李测试', 1.200000, 0.600000, 0, b'0', @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, '测试停用仓', '上海市松江区测试路 3 号', 3, '停用状态，用于验证 status 过滤', '王测试', NULL, NULL, 1, b'0', @creator, NOW(), @creator, NOW(), b'0', @tenant_id);

-- ----------------------------
-- 库存
-- 覆盖场景：同一商品分布在多仓、单仓、库存为 0
-- ----------------------------
DELETE FROM `erp_stock` WHERE `id` BETWEEN 9001 AND 9006;
INSERT INTO `erp_stock` (`id`, `product_id`, `warehouse_id`, `count`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, 9001, 9001, 120.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, 9001, 9002, 30.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, 9002, 9001, 45.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9004, 9003, 9001, 200.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9005, 9004, 9002, 15.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9006, 9005, 9001, 0.000000, @creator, NOW(), @creator, NOW(), b'0', @tenant_id);

-- ----------------------------
-- 客户
-- 覆盖场景：名称模糊命中多条、手机号精确匹配、停用客户、含财务敏感字段（能力视图不应返回）
-- ----------------------------
DELETE FROM `erp_customer` WHERE `id` IN (9001, 9002, 9003);
INSERT INTO `erp_customer` (`id`, `name`, `contact`, `mobile`, `telephone`, `email`, `fax`, `remark`, `status`, `sort`, `tax_no`, `tax_percent`, `bank_name`, `bank_account`, `bank_address`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, '测试客户-华东贸易', '陈测试', '13800138001', '021-10000001', 'east@example.com', NULL, 'ACF 联调用', 0, 1, 'TESTTAXNO0001', 6.000000, '测试银行上海分行', '6222000000000001', '上海市', @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, '测试客户-华南贸易', '刘测试', '13800138002', '020-10000002', 'south@example.com', NULL, 'ACF 联调用', 0, 2, 'TESTTAXNO0002', 6.000000, '测试银行广州分行', '6222000000000002', '广州市', @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, '测试客户-已停用', '赵测试', '13800138003', NULL, NULL, NULL, '停用状态', 1, 3, NULL, NULL, NULL, NULL, NULL, @creator, NOW(), @creator, NOW(), b'0', @tenant_id);

-- ----------------------------
-- 供应商
-- 覆盖场景：名称模糊命中多条、手机号模糊匹配、含财务敏感字段（能力视图不应返回）
-- ----------------------------
DELETE FROM `erp_supplier` WHERE `id` IN (9001, 9002, 9003);
INSERT INTO `erp_supplier` (`id`, `name`, `contact`, `mobile`, `telephone`, `email`, `fax`, `remark`, `status`, `sort`, `tax_no`, `tax_percent`, `bank_name`, `bank_account`, `bank_address`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(9001, '测试供应商-文具批发', '孙测试', '13900139001', '021-20000001', 'pen@example.com', NULL, 'ACF 联调用', 0, 1, 'TESTTAXNO1001', 13.000000, '测试银行上海分行', '6222000000001001', '上海市', @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9002, '测试供应商-纸品厂', '周测试', '13900139002', '021-20000002', 'paper@example.com', NULL, 'ACF 联调用', 0, 2, 'TESTTAXNO1002', 13.000000, '测试银行上海分行', '6222000000001002', '上海市', @creator, NOW(), @creator, NOW(), b'0', @tenant_id),
(9003, '测试供应商-电子配件', '吴测试', '13900139003', NULL, NULL, NULL, 'ACF 联调用', 0, 3, NULL, NULL, NULL, NULL, NULL, @creator, NOW(), @creator, NOW(), b'0', @tenant_id);
