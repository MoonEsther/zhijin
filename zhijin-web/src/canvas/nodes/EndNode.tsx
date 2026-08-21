import type { NodeProps } from '@xyflow/react';
import { NodeCard } from './NodeCard';

/** 结束节点：工作流出口，只有输入端口没有输出端口。主题色红色。 */
export function EndNode({ data }: NodeProps) {
  return <NodeCard title="结束" color="#ff4d4f" label={String(data.label ?? '')} source={false} />;
}
