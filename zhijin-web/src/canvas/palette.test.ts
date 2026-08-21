import { describe, it, expect } from 'vitest';
import { PALETTE, createCanvasNode } from './palette';

describe('palette 节点构造（V1 画布）', () => {
  it('面板包含 6 种节点类型，顺序与 NODE_TYPE_MAP 一致', () => {
    expect(PALETTE.map(p => p.type)).toEqual(['start', 'end', 'llm', 'tool', 'if', 'variable']);
  });

  it('createCanvasNode 生成带顶层 type 与 data.type 的节点（React Flow 渲染与 DSL 序列化各需其一）', () => {
    const node = createCanvasNode('llm', 3);
    expect(node.type).toBe('llm');
    expect(node.data.type).toBe('llm');
    expect(node.data.label).toBe('大模型');
    expect(node.position).toEqual({ x: 100 + 3 * 200, y: 100 });
    expect(node.id).toMatch(/^n-/);
  });
});
