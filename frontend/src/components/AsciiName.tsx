import figlet from 'figlet';
import big from 'figlet/importable-fonts/Big.js';
import { useMemo } from 'react';
import classes from './AsciiName.module.css';

figlet.parseFont('Big', big);

/**
 * A person's name drawn as an ASCII-art banner (figlet "Big" font). The banner is decorative and hidden from
 * assistive technology; the real name stays in the heading as visually hidden text. Names with characters the
 * font cannot draw fall back to the plain name.
 */
export function AsciiName({ name, as: Tag = 'h1' }: { name: string; as?: 'h1' | 'h2' | 'div' }) {
  const art = useMemo(() => {
    if (!/^[\x20-\x7e]+$/.test(name)) return null;
    try {
      return figlet.textSync(name, { font: 'Big', horizontalLayout: 'default' }).replace(/\s+$/gm, '').replace(/\n+$/, '');
    } catch {
      return null;
    }
  }, [name]);

  if (!art) return <Tag className={classes.plain}>{name}</Tag>;

  const cols = Math.max(...art.split('\n').map((line) => line.length));
  return (
    <Tag className={classes.wrap}>
      <span className={classes.srOnly}>{name}</span>
      <pre className={classes.art} aria-hidden="true" style={{ ['--cols' as string]: cols }}>
        {art}
      </pre>
    </Tag>
  );
}
