import { Button, Typography } from 'antd';
import type { Node } from '@xyflow/react';
import { NODE_TYPE_MAP, type FlowNodeData } from './dsl';

/** 画布节点统一类型：data 固定为 V1 前端 DSL 的 FlowNodeData（label + type）。 */
export type CanvasNode = Node<FlowNodeData>;

/** 节点面板条目：类型（与 NODE_TYPE_MAP 对齐）/ 中文名 / 主题色。 */
export interface PaletteItem {
  type: keyof typeof NODE_TYPE_MAP;
  label: string;
  color: string;
}

/** 左侧节点面板数据：6 种节点，颜色与 canvas/nodes 各节点组件一一对应。 */
export const PALETTE: PaletteItem[] = [
  { type: 'start', label: '开始', color: '#52c41a' },
  { type: 'end', label: '结束', color: '#ff4d4f' },
  { type: 'llm', label: '大模型', color: '#5C62FF' },
  { type: 'tool', label: '工具', color: '#fa8c16' },
  { type: 'if', label: '分支', color: '#722ed1' },
  { type: 'variable', label: '变量', color: '#8c8c8c' },
];

/**
 * 构造一个可放置到画布的初始节点：
 * - 顶层 type 供 React Flow 的 nodeTypes 匹配（决定渲染哪个节点组件）；
 * - data.type 供 toDsl 序列化时识别节点类型，二者缺一不可；
 * - position 按 index 横向排布，避免新加的节点叠在一起。
 * id 用时间戳 + 序号，规避同一毫秒内多次点击产生重复 id。
 */
export function createCanvasNode(type: PaletteItem['type'], index: number): CanvasNode {
  const label = PALETTE.find(p => p.type === type)?.label ?? type;
  return {
    id: `n-${Date.now().toString(36)}-${index}`,
    position: { x: 100 + index * 200, y: 100 },
    type,
    data: { label, type },
  };
}

/** 左侧节点面板：列出 6 种节点（名称 + 颜色圆点标识），点击通过 onAdd 回传类型追加到画布。 */
export function NodePalette({ onAdd }: { onAdd: (type: PaletteItem['type']) => void }) {
  return (
    <div style={{ width: 160, borderRight: '1px solid #f0f0f0', padding: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        节点组件
      </Typography.Text>
      {PALETTE.map(item => (
        <Button key={item.type} onClick={() => onAdd(item.type)} style={{ textAlign: 'left' }}>
          <span
            style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              borderRadius: '50%',
              background: item.color,
              marginRight: 8,
            }}
          />
          {item.label}
        </Button>
      ))}
    </div>
  );
}
