import type { NodeProps } from '@xyflow/react';
import { NodeCard } from './NodeCard';

/** 大模型节点：调用 LLM 完成生成任务，输入/输出端口齐全。主题色品牌蓝。 */
export function LlmNode({ data }: NodeProps) {
  return <NodeCard title="大模型" color="#5C62FF" label={String(data.label ?? '')} />;
}
