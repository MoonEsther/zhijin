import { Handle, Position } from '@xyflow/react';

/**
 * 节点通用卡片外壳：统一「白底卡片 + 主题色边框 + 输入/输出端口」的 antd 风格。
 * 6 种节点组件共用此壳，仅标题/颜色/端口有无不同，避免 6 份重复的样式与 Handle 逻辑。
 * 端口约定：开始节点是工作流入口没有输入端口（target=false），结束节点是出口没有输出端口（source=false）。
 */
interface NodeCardProps {
  /** 节点中文标题（开始/结束/大模型/工具/分支/变量） */
  title: string;
  /** 主题色（作为边框颜色），各节点类型用色区分 */
  color: string;
  /** 副标题：展示节点 label（来自 DSL config.label），可为空 */
  label: string;
  /** 是否渲染上方输入端口，默认 true；开始节点传 false */
  target?: boolean;
  /** 是否渲染下方输出端口，默认 true；结束节点传 false */
  source?: boolean;
}

export function NodeCard({ title, color, label, target = true, source = true }: NodeCardProps) {
  return (
    <div style={{ border: `1px solid ${color}`, borderRadius: 8, padding: 12, background: '#fff', minWidth: 140 }}>
      {target && <Handle type="target" position={Position.Top} />}
      <div style={{ fontWeight: 600 }}>{title}</div>
      <div style={{ fontSize: 12, color: '#888' }}>{label}</div>
      {source && <Handle type="source" position={Position.Bottom} />}
    </div>
  );
}
