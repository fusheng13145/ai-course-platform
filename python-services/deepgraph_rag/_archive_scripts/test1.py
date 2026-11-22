from neo4j import GraphDatabase
import config

# 连接 Neo4j
driver = GraphDatabase.driver(
    config.NEO4J_URI,
    auth=(config.NEO4J_USERNAME, config.NEO4J_PASSWORD)
)

# 定义函数：查询并打印所有节点
def get_all_nodes():
    with driver.session() as session:
        # 执行 Cypher 查询：获取所有节点
        result = session.run("MATCH (n) RETURN n LIMIT 100")
        
        print("=== 所有节点 ===")
        # 遍历结果
        for record in result:
            node = record["n"]  # 获取节点对象
            print(f"节点ID: {node.id}, 标签: {node.labels}, 属性: dict(node)")
def get_all_nodes_and_relationships():
    with driver.session() as session:
        result = session.run("MATCH (n)-[r]->(m) RETURN n, r, m LIMIT 50")
        
        print("=== 节点与关系 ===")
        for record in result:
            print("起点:", dict(record["n"]))
            print("关系类型:", record["r"].type)
            print("终点:", dict(record["m"]))
            print("-" * 40)

# 调用函数
if __name__ == "__main__":
    get_all_nodes()
    get_all_nodes_and_relationships()
    driver.close()  # 关闭连接