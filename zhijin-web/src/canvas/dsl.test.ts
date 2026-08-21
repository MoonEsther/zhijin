import { describe, it, expect } from 'vitest';
import { toDsl, fromDsl } from './dsl';

describe('dsl 转换（V1 前端内部草稿格式）', () => {
  it('React Flow 节点 → DSL', () => {
    const dsl = toDsl(
      [
        { id: 'start', data: { label: '开始', type: 'start' } },
        { id: 'llm', data: { label: '大模型', type: 'llm' } },
        { id: 'end', data: { label: '结束', type: 'end' } },
      ],
      [{ id: 'e1', source: 'start', target: 'llm' }, { id: 'e2', source: 'llm', target: 'end' }],
    );
    expect(dsl.start).toBe('start');
    expect(dsl.nodes).toHaveLength(3);
    expect(dsl.edges[0]).toEqual({ from: 'start', to: 'llm' });
  });

  it('DSL → React Flow 节点', () => {
    const { nodes, edges } = fromDsl({
      nodes: [
        { id: 'start', type: 'start', config: { label: '开始' } },
        { id: 'llm', type: 'llm' },
      ],
      edges: [{ from: 'start', to: 'llm' }],
    });
    expect(nodes).toHaveLength(2);
    expect(nodes[0].data.type).toBe('start');
    expect(edges[0].source).toBe('start');
    expect(edges[0].target).toBe('llm');
  });
});
