import type { NodeProps } from '@xyflow/react';
import { NodeCard } from './NodeCard';

/** 分支节点：条件判断分流，输入/输出端口齐全。主题色紫色。 */
export function IfNode({ data }: NodeProps) {
  return <NodeCard title="分支" color="#722ed1" label={String(data.label ?? '')} />;
}
