-- ============================================
-- Part 6: Mock数据续 - 权限/通知/配置
-- ============================================
USE ai_course_platform;

-- 资源数据
INSERT INTO `resources` (id,file_name,file_type,url,course_id,uploader_id,size) VALUES
(1,"Python第一章.pptx","pptx","/uploads/python_ch1.pptx",1,2,2048000),
(2,"Python变量类型.pdf","pdf","/uploads/python_ch2.pdf",1,2,1536000),
(3,"数据结构线性表.pptx","pptx","/uploads/ds_ch1.pptx",2,2,3072000),
(4,"ML概述.pdf","pdf","/uploads/ml_ch1.pdf",3,2,1024000);

-- 作业数据
INSERT INTO `tasks` (id,title,task_type,course_id,creator_id,due_date,max_score,is_published,status) VALUES
(1,"Python第一次作业 - 基础练习","homework",1,2,DATE_ADD(NOW(),INTERVAL 7 DAY),100,1,"published"),
(2,"Python第二次作业 - 成绩管理系统","homework",1,2,DATE_ADD(NOW(),INTERVAL 14 DAY),100,1,"published"),
(3,"数据结构作业 - 链表操作","homework",2,2,DATE_ADD(NOW(),INTERVAL 10 DAY),100,1,"published"),
(4,"ML作业 - 线性回归预测","project",3,2,DATE_ADD(NOW(),INTERVAL 21 DAY),100,1,"published"),
(5,"高等数学练习 - 极限","homework",4,3,DATE_ADD(NOW(),INTERVAL 5 DAY),100,0,"draft");

-- 提交数据
INSERT INTO `submissions` (id,task_id,student_id,content,status,score,feedback) VALUES
(1,1,4,"已完成基础语法练习","GRADED",92,"完成度高，注意字符串格式化"),
(2,1,5,"已完成作业","SUBMITTED",NULL,NULL),
(3,2,4,"学生成绩管理系统已实现","GRADED",88,"功能完整，建议增加异常处理"),
(4,3,4,"链表操作已实现并测试","GRADED",95,"实现规范，考虑边界情况");

-- 学习路径
INSERT INTO `learning_paths` (id,student_id,name,goal,status,progress,estimated_days,generated_by) VALUES
(1,4,"Python进阶学习路径","掌握Python高级编程","ACTIVE",45,60,"AI"),
(2,8,"机器学习入门路径","独立完成ML项目","ACTIVE",30,90,"AI");

INSERT INTO `learning_path_nodes` (id,path_id,knowledge_point_id,node_name,node_type,sort_order,is_completed,estimated_minutes) VALUES
(1,1,1,"复习Python基础语法","knowledge",1,1,60),
(2,1,2,"深入学习数据类型","knowledge",2,1,45),
(3,1,4,"掌握函数式编程","knowledge",3,0,90),
(4,2,7,"学习监督学习基础","knowledge",1,0,120);

-- 学习记录
INSERT INTO `learning_records` (id,student_id,course_id,activity_type,duration,study_date) VALUES
(1,4,1,"video",3600,CURDATE()),
(2,4,1,"quiz",1800,DATE_SUB(CURDATE(),INTERVAL 1 DAY)),
(3,4,2,"video",2700,DATE_SUB(CURDATE(),INTERVAL 2 DAY)),
(4,5,1,"video",1800,CURDATE()),
(5,8,3,"video",4500,DATE_SUB(CURDATE(),INTERVAL 1 DAY));

-- 通知
INSERT INTO `notifications` (id,user_id,title,content,category,priority,is_read) VALUES
(1,4,"作业已批改","Python第一次作业已批改，得分92分","learning","normal",0),
(2,4,"课程更新","数据库原理课程已更新新章节","learning","low",1),
(3,5,"作业截止提醒","Python作业将于3天后截止","learning","high",0);

-- 角色权限
INSERT INTO `role_permissions` (id,role,permission_key,permission_name,permission_type,sort_order,is_active) VALUES
(1,"ADMIN","user:read","查看用户","MENU",1,1),
(2,"ADMIN","user:create","创建用户","BUTTON",2,1),
(3,"ADMIN","user:delete","删除用户","BUTTON",3,1),
(4,"ADMIN","course:approve","审核课程","BUTTON",4,1),
(5,"ADMIN","system:config","系统配置","MENU",5,1),
(6,"ADMIN","analytics:view","查看分析","MENU",6,1),
(7,"TEACHER","course:create","创建课程","BUTTON",1,1),
(8,"TEACHER","task:grade","批改任务","BUTTON",2,1),
(9,"TEACHER","question:ai_generate","AI生成题目","BUTTON",3,1),
(10,"TEACHER","analytics:view","查看分析","MENU",4,1),
(11,"STUDENT","course:enroll","选课","BUTTON",1,1),
(12,"STUDENT","task:submit","提交任务","BUTTON",2,1),
(13,"STUDENT","exam:take","参加考试","BUTTON",3,1),
(14,"STUDENT","wrong_question:manage","管理错题","MENU",4,1),
(15,"STUDENT","learning_path:view","查看学习路径","MENU",5,1),
(16,"STUDENT","ai:chat","AI对话","BUTTON",6,1),
(17,"STUDENT","analytics:view","查看分析","MENU",7,1);

-- 系统配置
INSERT INTO `system_configs` (id,config_key,config_value,config_group,description) VALUES
(1,"site_name","AI智能课程平台","general","站点名称"),
(2,"allow_register","true","general","是否允许注册"),
(3,"max_upload_size","50","file","最大上传大小(MB)"),
(4,"ai_enabled","true","ai","是否启用AI"),
(5,"ai_model","deepseek-chat","ai","AI模型名称");

-- 试卷数据
INSERT INTO `exam_papers` (id,title,course_id,creator_id,status,total_score,duration,question_count,is_active) VALUES
(1,"Python基础测试",1,2,"published",100,60,3,1),
(2,"数据结构期中考试",2,2,"draft",100,90,2,1);

INSERT INTO `exam_questions` (id,exam_paper_id,question_id,order_index,points) VALUES
(1,1,1,1,5),(2,1,4,2,5),(3,1,6,3,15),
(4,2,2,1,5),(5,2,8,2,10);

-- 错题数据
INSERT INTO `wrong_questions` (id,student_id,question_id,source,student_answer,correct_answer,is_mastered,wrong_count) VALUES
(1,4,2,"exam","[""A""]","[""B""]",0,2),
(2,4,5,"exam","[""D""]","[""C""]",1,1);
