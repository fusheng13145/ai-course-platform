-- ============================================
-- Part 5: 完整Mock数据 (密码均为 123456)
-- ============================================
USE ai_course_platform;

-- 1. 用户数据 (8个)
INSERT INTO `users` (id,email,username,password,full_name,role,is_active,is_verified,phone,student_id,major,grade,class_name,class_id,department,title,bio,failed_login_attempts,created_at) VALUES
(1,"admin@aicourse.com","admin","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","系统管理员","ADMIN",1,1,"13800000001",NULL,NULL,NULL,NULL,NULL,"技术部","高级工程师","系统管理员",0,NOW()),
(2,"teacher@aicourse.com","teacher","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","张教授","TEACHER",1,1,"13800000002",NULL,NULL,NULL,NULL,NULL,"计算机学院","教授","人工智能方向导师",0,NOW()),
(3,"teacher2@aicourse.com","teacher2","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","李副教授","TEACHER",1,1,"13800000003",NULL,NULL,NULL,NULL,NULL,"数学学院","副教授","数学建模指导老师",0,NOW()),
(4,"student@aicourse.com","student","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","王同学","STUDENT",1,1,"13800000004","2024001","计算机科学与技术","2024级","计算机1班",1,"计算机学院",NULL,"大二学生",0,NOW()),
(5,"student2@aicourse.com","student2","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","赵同学","STUDENT",1,0,"13800000005","2024002","计算机科学与技术","2024级","计算机1班",1,"计算机学院",NULL,"勤奋好学",0,NOW()),
(6,"student3@aicourse.com","student3","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","刘同学","STUDENT",1,0,"13800000006","2024003","软件工程","2024级","软件1班",2,"计算机学院",NULL,"擅长算法",0,NOW()),
(7,"student4@aicourse.com","student4","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","陈同学","STUDENT",1,0,"13800000007","2024004","软件工程","2024级","软件1班",2,"计算机学院",NULL,"后端开发爱好者",0,NOW()),
(8,"student5@aicourse.com","student5","$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy","杨同学","STUDENT",1,0,"13800000008","2024005","人工智能","2023级","AI班",3,"计算机学院",NULL,"热爱AI",0,NOW());

-- 2. 班级数据 (4个)
INSERT INTO `classrooms` (id,name,code,description,grade_level,major,advisor_id,student_count,max_students) VALUES
(1,"计算机1班","CS2401","计算机2024级1班","2024级","计算机科学与技术",2,2,50),
(2,"软件1班","SE2401","软件工程2024级1班","2024级","软件工程",2,2,50),
(3,"AI班","AI2301","人工智能2023级1班","2023级","人工智能",3,1,30),
(4,"数学1班","MA2401","数学2024级1班","2024级","数学与应用数学",3,0,50);

-- 3. 课程数据 (6门)
INSERT INTO `courses` (id,name,code,description,credits,teacher_id,semester,year,status,approval_status,enrollment_type,max_students,category,difficulty,tags,start_date,end_date) VALUES
(1,"Python程序设计","CS101","Python编程入门课程",4,2,"春季",2025,"active","APPROVED","FREE",100,"编程开发","easy","Python,编程入门","2025-02-24","2025-07-05"),
(2,"数据结构与算法","CS201","常见数据结构与经典算法",5,2,"春季",2025,"active","APPROVED","FREE",80,"计算机科学","hard","数据结构,算法","2025-02-24","2025-07-05"),
(3,"机器学习基础","AI301","ML基本概念与实战",4,2,"春季",2025,"active","APPROVED","FREE",60,"人工智能","medium","机器学习,AI","2025-02-24","2025-07-05"),
(4,"高等数学A","MA101","高等数学基础课程",6,3,"春季",2025,"active","APPROVED","FREE",120,"数学","medium","高等数学,微积分","2025-02-24","2025-07-05"),
(5,"Web前端开发","CS301","HTML/CSS/JS/Vue.js",4,2,"春季",2025,"active","APPROVED","APPROVAL_REQUIRED",50,"编程开发","medium","前端,Vue.js","2025-02-24","2025-07-05"),
(6,"数据库原理与应用","CS202","关系数据库与SQL语言",4,2,"春季",2025,"active","APPROVED","FREE",80,"计算机科学","medium","数据库,SQL","2025-02-24","2025-07-05");

-- 4. 选课数据
INSERT INTO `course_enrollments` (id,student_id,course_id,progress,assignment_score,exam_score) VALUES
(1,4,1,85,88,82),(2,4,2,60,75,65),(3,4,3,45,70,NULL),
(4,5,1,75,82,78),(5,5,4,90,95,88),
(6,6,2,55,68,60),(7,6,5,30,NULL,NULL),
(8,7,1,70,80,72),(9,7,6,40,NULL,NULL),
(10,8,3,95,92,96);

-- 5. 章节数据
INSERT INTO `course_chapters` (id,course_id,parent_id,chapter_name,sort_order) VALUES
(1,1,NULL,"第一章 Python入门",1),(2,1,NULL,"第二章 变量与数据类型",2),(3,1,NULL,"第三章 流程控制",3),
(4,1,1,"1.1 Python简介",1),(5,1,1,"1.2 环境搭建",2),
(6,2,NULL,"第一章 线性表",1),(7,2,NULL,"第二章 树与二叉树",2),
(8,3,NULL,"第一章 机器学习概述",1),(9,3,NULL,"第二章 线性回归",2);

-- 6. 题目数据 (8道)
INSERT INTO `questions` (id,type,content,options,correct_answers,difficulty,score,course_id,creator_id,explanation) VALUES
(1,"single","Python定义函数的关键字是？","{""A"":""def"",""B"":""function"",""C"":""define"",""D"":""func""}","[""A""]","easy",5,1,2,"def关键字"),
(2,"single","哪种数据结构是LIFO？","{""A"":""队列"",""B"":""栈"",""C"":""链表"",""D"":""数组""}","[""B""]","easy",5,2,2,"栈是LIFO"),
(3,"multiple","哪些属于监督学习？","{""A"":""线性回归"",""B"":""K-means"",""C"":""SVM"",""D"":""决策树""}","[""A"",""C"",""D""]","medium",10,3,2,"K-means是无监督学习"),
(4,"judge","Python列表可变而元组不可变。","{""A"":""正确"",""B"":""错误""}","[""A""]","easy",5,1,2,"列表可变元组不可变"),
(5,"single","事务的ACID特性不包括？","{""A"":""原子性"",""B"":""一致性"",""C"":""高效性"",""D"":""持久性""}","[""C""]","medium",5,6,2,"ACID=原子性/一致性/隔离性/持久性"),
(6,"essay","简述面向对象三大特性。",NULL,"[""封装、继承、多态""]","medium",15,1,2,"封装继承多态"),
(7,"single","Vue.js双向数据绑定指令？","{""A"":""v-bind"",""B"":""v-model"",""C"":""v-on"",""D"":""v-for""}","[""B""]","easy",5,5,2,"v-model"),
(8,"fill","前序遍历顺序：根->__->__",NULL,"[""左子树"",""右子树""]","medium",10,2,2,"根左右");

-- 7. 知识点数据
INSERT INTO `knowledge_points` (id,name,description,course_id,parent_id,level,sort_order,importance,difficulty) VALUES
(1,"Python基础语法","基本语法规则",1,NULL,0,1,5,"easy"),
(2,"变量与数据类型","变量和数据类型",1,1,1,2,4,"easy"),
(3,"控制流程","条件判断和循环",1,1,1,3,5,"easy"),
(4,"函数定义","函数的定义和调用",1,1,1,4,4,"easy"),
(5,"线性表","数组链表栈队列",2,NULL,0,1,5,"medium"),
(6,"树结构","二叉树遍历",2,NULL,0,2,5,"hard"),
(7,"监督学习","线性回归决策树SVM",3,NULL,0,1,5,"medium");
