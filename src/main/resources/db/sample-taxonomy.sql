-- =============================================================
--  示例分类与标签（Java 后端技术博主可用，可重复执行：INSERT IGNORE）
--  用法：mysql -uroot -p blog < sample-taxonomy.sql
-- =============================================================
USE `blog`;

-- ---------- 一级分类 ----------
INSERT IGNORE INTO `t_category` (`name`, `slug`, `description`, `parent_id`, `sort`, `article_count`) VALUES
('后端开发',   'backend',      'Java 后端核心技术：语言基础、JVM、并发、Spring 全家桶', 0, 60, 0),
('数据存储',   'database',     '关系型与非关系型数据库：MySQL、Redis、Elasticsearch',    0, 50, 0),
('中间件',     'middleware',   '消息队列、缓存、网关等基础设施组件',                    0, 40, 0),
('架构设计',   'architecture', '微服务、分布式、高可用与系统设计',                      0, 30, 0),
('工具与实践', 'tools',        '构建工具、IDE、Linux、Docker、CI/CD',                   0, 20, 0),
('面试与成长', 'career',       '面试题、源码阅读、踩坑记录与技术成长',                  0, 10, 0);

-- ---------- 二级分类（挂载到对应一级分类下） ----------
SET @backend      = (SELECT `id` FROM `t_category` WHERE `slug` = 'backend');
SET @database     = (SELECT `id` FROM `t_category` WHERE `slug` = 'database');
SET @middleware   = (SELECT `id` FROM `t_category` WHERE `slug` = 'middleware');
SET @architecture = (SELECT `id` FROM `t_category` WHERE `slug` = 'architecture');
SET @tools        = (SELECT `id` FROM `t_category` WHERE `slug` = 'tools');

INSERT IGNORE INTO `t_category` (`name`, `slug`, `description`, `parent_id`, `sort`, `article_count`) VALUES
('Java 基础',   'java-basic',   '语法、集合、IO、反射与泛型',        @backend,      95, 0),
('JVM 与调优',  'jvm',          '内存模型、垃圾回收、类加载与调优',  @backend,      90, 0),
('并发编程',    'concurrency',  '线程池、锁、JUC 与并发容器',        @backend,      85, 0),
('Spring',      'spring',       'Spring Framework / Boot / Cloud',  @backend,      80, 0),
('MySQL',       'mysql',        '索引、事务、锁、SQL 优化',          @database,     90, 0),
('Redis',       'redis',        '数据结构、持久化、分布式锁、缓存',  @database,     85, 0),
('消息队列',    'mq',           'Kafka、RabbitMQ、RocketMQ',         @middleware,   90, 0),
('分布式',      'distributed',  '一致性、分布式事务、CAP 与共识算法', @architecture, 90, 0),
('微服务',      'microservice', '注册中心、网关、熔断限流、链路追踪', @architecture, 85, 0),
('DevOps',      'devops',       'Linux、Docker、Kubernetes、CI/CD', @tools,        90, 0);

-- ---------- 标签 ----------
INSERT IGNORE INTO `t_tag` (`name`, `slug`, `color`) VALUES
('Java',            'java',            '#e11d48'),
('JVM',             'jvm',             '#f97316'),
('并发编程',        'concurrency',     '#dc2626'),
('多线程',          'multithreading',  '#ef4444'),
('集合框架',        'collections',     '#f43f5e'),
('反射与泛型',      'reflect-generic', '#fb7185'),
('Spring Boot',     'spring-boot',     '#16a34a'),
('Spring Cloud',    'spring-cloud',    '#22c55e'),
('Spring MVC',      'spring-mvc',      '#4ade80'),
('MyBatis',         'mybatis',         '#0ea5e9'),
('MySQL',           'mysql',           '#2563eb'),
('索引优化',        'index-tuning',    '#3b82f6'),
('事务',            'transaction',     '#1d4ed8'),
('Redis',           'redis',           '#dc2626'),
('缓存',            'cache',           '#f59e0b'),
('分布式锁',        'distributed-lock','#d97706'),
('Kafka',           'kafka',           '#6366f1'),
('RabbitMQ',        'rabbitmq',        '#8b5cf6'),
('消息队列',        'message-queue',   '#7c3aed'),
('微服务',          'microservice',    '#0891b2'),
('分布式',          'distributed',     '#0e7490'),
('设计模式',        'design-pattern',  '#059669'),
('算法',            'algorithm',       '#65a30d'),
('数据结构',        'data-structure',  '#84cc16'),
('性能优化',        'performance',     '#eab308'),
('源码阅读',        'source-code',     '#a16207'),
('Linux',           'linux',           '#475569'),
('Docker',          'docker',          '#0284c7'),
('Kubernetes',      'kubernetes',      '#326ce5'),
('Maven',           'maven',           '#c71a36'),
('Git',             'git',             '#f05033'),
('Nginx',           'nginx',           '#009639'),
('Docker Compose',  'docker-compose',  '#099cec'),
('踩坑记录',        'pitfall',         '#be185d'),
('面试题',          'interview',       '#7c2d12');
