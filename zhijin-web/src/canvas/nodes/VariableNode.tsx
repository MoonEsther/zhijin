import type { NodeProps } from '@xyflow/react';
import { NodeCard } from './NodeCard';

/** 变量节点：声明/引用变量，输入/输出端口齐全。主题色灰色。 */
export function VariableNode({ data }: NodeProps) {
  return <NodeCard title="变量" color="#8c8c8c" label={String(data.label ?? '')} />;
}
