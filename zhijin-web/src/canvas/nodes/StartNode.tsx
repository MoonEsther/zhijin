import type { NodeProps } from '@xyflow/react';
import { NodeCard } from './NodeCard';

/** 开始节点：工作流唯一入口，只有输出端口没有输入端口。主题色绿色。 */
export function StartNode({ data }: NodeProps) {
  return <NodeCard title="开始" color="#52c41a" label={String(data.label ?? '')} target={false} />;
}
