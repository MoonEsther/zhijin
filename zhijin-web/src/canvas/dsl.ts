// V1 前端内部草稿格式（localStorage），后端 DSL 对接留 V2（D4）。
// 注意：V1 的 {from,to} 边与后端 §7.2 Connection(fromNode,toNode) 字段不一致，V1 不落后端。
export const NODE_TYPE_MAP = {
  start: 'start', end: 'end', llm: 'llm', tool: 'tool', if: 'if', variable: 'variable',
} as const;

export interface FlowNodeData { label: string; type: keyof typeof NODE_TYPE_MAP; [k: string]: unknown }
export interface FlowEdgeData { source: string; target: string; id: string }

/** React Flow 节点/边 → 前端内部 DSL JSON（start 取第一个 start 类型节点，无则回退首个节点）。 */
export function toDsl(nodes: { id: string; data: FlowNodeData }[], edges: FlowEdgeData[]) {
  return {
    id: `wf-${Date.now().toString(36)}`,
    start: nodes.find(n => n.data.type === 'start')?.id ?? nodes[0]?.id ?? '',
    nodes: nodes.map(n => ({ id: n.id, type: NODE_TYPE_MAP[n.data.type], config: { label: n.data.label } })),
    edges: edges.map(e => ({ from: e.source, to: e.target })),
  };
}

/** 前端内部 DSL JSON → React Flow 节点/边（布局为简易横向排布，正式布局留画布实现）。 */
export function fromDsl(dsl: { nodes: {id: string; type: string; config?: Record<string, unknown>}[]; edges: {from: string; to: string}[] }) {
  const nodes = dsl.nodes.map((n, i) => ({
    id: n.id,
    position: { x: 100 + i * 200, y: 100 },
    data: { label: n.config?.label ?? n.type, type: n.type } as FlowNodeData,
  }));
  const edges = dsl.edges.map((e, i) => ({ id: `e${i}`, source: e.from, target: e.to }));
  return { nodes, edges };
}
