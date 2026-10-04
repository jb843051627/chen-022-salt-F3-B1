-- salt 食盐专营管理与食盐质量安全监管 -- schema (chen-022)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_022

CREATE TABLE IF NOT EXISTS t_salt_ent (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '食盐定点企业代号',
  site_name varchar(128) DEFAULT NULL COMMENT '企业全称',
  site_type varchar(32) DEFAULT NULL COMMENT '企业类别',
  road_name varchar(128) DEFAULT NULL COMMENT '属地那一路(省—市—县)',
  status int DEFAULT NULL COMMENT '名录情形 0在册 1已摘牌',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='食盐定点企业总名录';

CREATE TABLE IF NOT EXISTS t_salt_iod_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '碘含量分档判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '起算数值(毫克每千克)',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '够线数值(毫克每千克)',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶数值(毫克每千克)',
  eff_start datetime DEFAULT NULL COMMENT '立线之日',
  eff_end datetime DEFAULT NULL COMMENT '让位之日(不含)',
  priority int DEFAULT NULL COMMENT '让位顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在场 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='碘含量分档判定线';

CREATE TABLE IF NOT EXISTS t_salt_link_page (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '企业品种挂接代号',
  node_no int DEFAULT NULL COMMENT '适用区域层级序',
  site_id int DEFAULT NULL COMMENT '所属定点企业',
  site_no varchar(64) DEFAULT NULL COMMENT '所属企业代号',
  should_count int DEFAULT NULL COMMENT '应挂品种数',
  done_count int DEFAULT NULL COMMENT '已挂品种数',
  lack_count int DEFAULT NULL COMMENT '欠挂品种数',
  content varchar(255) DEFAULT NULL COMMENT '随页交来的标签要件',
  status int DEFAULT NULL COMMENT '挂接进展 0待挂 1已挂讫 2压页',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业品种挂接簿页';

-- t_salt_record_flow（备案单，一企一活单：在办与止住的生成列命中唯一键；
--   步次只以系统按已过档次数出来的 stage 为准，表单递来的 req_stage 仅作来意，入库/回读都不看它）
CREATE TABLE IF NOT EXISTS t_salt_record_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '跨省经营备案单代号',
  ent_no varchar(64) DEFAULT NULL COMMENT '企业代号（四样齐之一）',
  depart_at datetime DEFAULT NULL COMMENT '承运那一路起运那一刻（四样齐之二）',
  arrive_at datetime DEFAULT NULL COMMENT '到货那一刻（四样齐之三）',
  region_count int DEFAULT NULL COMMENT '拟销到的县域个数（四样齐之四）',
  check_required int DEFAULT '3' COMMENT '材料核验档应填全的核验笔数（当地定的那几笔）',
  publicity_start datetime DEFAULT NULL COMMENT '材料齐那一刻起钟（推进0→1落定，回退打断即清零）',
  publicity_hours decimal(8,2) DEFAULT NULL COMMENT '当地定的应连着公示的钟点数',
  publicity_done_hours decimal(8,2) DEFAULT NULL COMMENT '已连着公示的钟点数（与起钟一刻同一次算出）',
  cert_returned int DEFAULT '0' COMMENT '旧证缴回回执落没落 0没落 1已落',
  close_type varchar(16) DEFAULT NULL COMMENT '收口说法 N不必备案 S就地止住；落档封存则留空',
  stage int DEFAULT NULL COMMENT '当前步次 0..3（递单受理/材料核验/生效确认/缴回封卷，系统按已过步次数出来）',
  status int DEFAULT NULL COMMENT '备案单情形 0未起 1在办 2已封卷落档 3收口止住；落档与止住都封口（改不动/删不掉/挪不了）',
  content varchar(255) DEFAULT NULL COMMENT '当前档最近一遍留下的话（同档第二遍不另起一行）',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪步动作',
  active_key varchar(64) GENERATED ALWAYS AS (CASE WHEN status IN (1,3) THEN ent_no ELSE NULL END) VIRTUAL COMMENT '在办/止住活单占用键（生成列）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除；已封卷的单禁止物理删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  UNIQUE KEY uk_active_ent (active_key),
  KEY idx_biz (biz_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨省经营备案单';

-- 材料核验笔（第二步只认核验笔填全没有；填全一刻自动起公示钟）
CREATE TABLE IF NOT EXISTS t_salt_record_check (
  id bigint NOT NULL COMMENT '主键',
  flow_id bigint NOT NULL COMMENT '所属备案单',
  item_no varchar(64) DEFAULT NULL COMMENT '核验那几笔的笔号',
  item_name varchar(128) DEFAULT NULL COMMENT '核验项名目',
  filled int DEFAULT '0' COMMENT '填全没有 0空 1填全',
  fill_at datetime DEFAULT NULL COMMENT '落齐那一刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_flow (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨省经营备案单核验笔';

-- 流转段记载：每趟每进/退一档落一段（退档后重走另起一趟，旧段沉底保留不删）；
-- 同一步第二遍空递不产生新段；两条来路挤进同一秒钟，各记各的（event_ms 相同也保留两行）。
CREATE TABLE IF NOT EXISTS t_salt_record_note (
  id bigint NOT NULL COMMENT '主键',
  flow_id bigint NOT NULL COMMENT '所属备案单',
  stage_no int DEFAULT NULL COMMENT '这一段对的步次 0..3；收口段记止住时所在档；4=落档封存段',
  walk_no int DEFAULT NULL COMMENT '这一档走到第几趟（头趟1，退回复走累加）',
  seq int DEFAULT NULL COMMENT '段次（本单内连续递增；倒着数回递单受理要与受理簿那几笔合得拢）',
  action varchar(64) DEFAULT NULL COMMENT '这一段的动作 accept/advance/rollback/close/seal',
  said varchar(255) DEFAULT NULL COMMENT '哪一步写了什么话',
  event_ms bigint DEFAULT NULL COMMENT '落在哪一刻（毫秒；同秒两笔不并成一笔也不少记一笔）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间（该段落下那一刻）',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_stage_walk (flow_id, stage_no, walk_no),
  KEY idx_flow_seq (flow_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨省经营备案单流转段记载';

CREATE TABLE IF NOT EXISTS t_salt_renew_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '延续换证核签单',
  node_no int DEFAULT NULL COMMENT '当前所在档口 0..2（县初核/市复审/省核准）',
  sign_mode int DEFAULT NULL COMMENT '同档算齐办法 0一名即可 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本档应落人数',
  sign_count int DEFAULT NULL COMMENT '本档已落印数',
  status int DEFAULT NULL COMMENT '核签情形 0在核 1已办结 2已挪回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='延续换证核签单';

CREATE TABLE IF NOT EXISTS t_salt_spot_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '监督抽检结果报送册码',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的定点企业代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行不合格件数',
  hours_num decimal(12,2) DEFAULT NULL COMMENT '本行送检件数',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽检结果报送核收行';

CREATE TABLE IF NOT EXISTS t_salt_year_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '年检催办条目代号',
  due_at datetime DEFAULT NULL COMMENT '应开口那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '最可提前几日开口',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对企业的记要',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派出 2分不出',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='年检到期催办单';

-- 初始档案数据（状态约定：id=1 启用 / id=2 停用）
-- t_salt_ent 预置两条种子名录：id=0 在册、id=1 已摘牌；企业代号与类别按各表既有排法给值。
INSERT IGNORE INTO t_salt_ent (id, site_no, site_name, site_type, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'JY00', '盐泽制盐一厂（制盐企业，在册）', '制盐企业', '盐岭省—盐泽市—临卤县', 0, 0, 'seed', NOW()),
       (1, 'JY01', '青卤批发部（转行后摘牌）', '省内批发企业', '盐岭省—盐泽市—青卤县', 1, 0, 'seed', NOW());


-- 菜单与授权（挂在系统管理目录 592059865673760768 下；授权管理员角色 488243256161730560；可重复执行）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (710103000000000001, '跨省经营备案单', '跨省经营备案单流转台账', '/saltRecordFlow/view', 0, 592059865673760768, 'saltRecordFlow:view', 1, 'fa fa-file-text-o', 20, 0, 'seed', NOW()),
       (710103000000000002, '备案单集合', '流转台账与在办张数', '/saltRecordFlow/list', 0, 710103000000000001, 'saltRecordFlow:list', 2, NULL, 1, 0, 'seed', NOW()),
       (710103000000000003, '备案单立案', '立案递单', '/saltRecordFlow/accept', 0, 710103000000000001, 'saltRecordFlow:accept', 2, NULL, 2, 0, 'seed', NOW()),
       (710103000000000004, '备案单推进', '往前挪步/核验笔/回执', '/saltRecordFlow/advance', 0, 710103000000000001, 'saltRecordFlow:advance', 2, NULL, 3, 0, 'seed', NOW()),
       (710103000000000005, '备案单回退', '往后收一档', '/saltRecordFlow/rollback', 0, 710103000000000001, 'saltRecordFlow:rollback', 2, NULL, 4, 0, 'seed', NOW()),
       (710103000000000006, '备案单收口', '不必备案/就地止住', '/saltRecordFlow/close', 0, 710103000000000001, 'saltRecordFlow:close', 2, NULL, 5, 0, 'seed', NOW()),
       (710103000000000007, '备案单倒推复核', '段数倒着数回递单受理', '/saltRecordFlow/audit', 0, 710103000000000001, 'saltRecordFlow:audit', 2, NULL, 6, 0, 'seed', NOW());

INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
VALUES (710103000000010001, 488243256161730560, 710103000000000001, 'seed', NOW()),
       (710103000000010002, 488243256161730560, 710103000000000002, 'seed', NOW()),
       (710103000000010003, 488243256161730560, 710103000000000003, 'seed', NOW()),
       (710103000000010004, 488243256161730560, 710103000000000004, 'seed', NOW()),
       (710103000000010005, 488243256161730560, 710103000000000005, 'seed', NOW()),
       (710103000000010006, 488243256161730560, 710103000000000006, 'seed', NOW()),
       (710103000000010007, 488243256161730560, 710103000000000007, 'seed', NOW());
