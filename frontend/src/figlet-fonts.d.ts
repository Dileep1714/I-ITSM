// figlet ships its browser-importable fonts as plain JS modules without type declarations.
declare module 'figlet/importable-fonts/*.js' {
  const font: string;
  export default font;
}
