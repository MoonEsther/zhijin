import type { NodeProps } from '@xyflow/react';
import { NodeCard } from './NodeCard';

/** 工具节点：调用外部工具/函数，输入/输出端口齐全。主题色橙色。 */
export function ToolNode({ data }: NodeProps) {
  return <NodeCard title="工具" color="#fa8c16" label={String(data.label ?? '')} />;
}
