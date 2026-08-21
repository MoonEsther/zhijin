import { forwardRef, useImperativeHandle } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { Node, Edge, Connection, NodeTypes } from '@xyflow/react';
import { StartNode, EndNode, LlmNode, ToolNode, IfNode, VariableNode } from './nodes';
import { createCanvasNode, type CanvasNode, type PaletteItem } from './palette';

// 节点类型 → 渲染组件映射（key 与 NODE_TYPE_MAP 的节点类型一一对应）
const nodeTypes: NodeTypes = {
  start: StartNode,
  end: EndNode,
  llm: LlmNode,
  tool: ToolNode,
  if: IfNode,
  variable: VariableNode,
};

/** 父组件（详情页）通过 ref 操作画布：追加节点 / 读取快照用于保存。 */
export interface FlowCanvasHandle {
  /** 从左侧面板追加一个指定类型的新节点（自动定位，避免重叠）。 */
  addNode: (type: PaletteItem['type']) => void;
  /** 读取当前画布节点/边快照（「保存画布」时 toDsl 用）。 */
  getSnapshot: () => { nodes: CanvasNode[]; edges: Edge[] };
}

interface Props {
  initialNodes: CanvasNode[];
  initialEdges: Edge[];
}

/**
 * React Flow 工作流画布。
 * 注意：节点/边状态由本组件持有（useNodesState/useEdgesState 只在首次挂载读取 initialNodes），
 * 因此调用方必须以 key={appId} 强制重挂载，否则从 /apps/1 切到 /apps/2 会残留上一应用的画布内容。
 */
export const FlowCanvas = forwardRef<FlowCanvasHandle, Props>(function FlowCanvas({ initialNodes, initialEdges }, ref) {
  const [nodes, setNodes, onNodesChange] = useNodesState<CanvasNode>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  // 暴露给父组件的命令式操作；依赖 nodes/edges 以便 getSnapshot 拿到最新快照
  useImperativeHandle(
    ref,
    () => ({
      addNode: (type) => setNodes(ns => [...ns, createCanvasNode(type, ns.length)]),
      getSnapshot: () => ({ nodes, edges }),
    }),
    [nodes, edges],
  );

  // 拖拽连线：新增一条 source→target 边（默认贝塞尔曲线）
  const onConnect = (c: Connection) => setEdges(es => addEdge(c, es));

  return (
    <div style={{ height: 560 }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        fitView
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
});
