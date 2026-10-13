"""Generate the local SRS PDF from its editable Markdown source.

Requires reportlab. Example:
  python -I docs/tools/build_srs.py --font-dir C:/Windows/Fonts
Optional --library-dir points to an isolated directory containing reportlab.
This script intentionally supports only the simple headings/paragraphs/lists used
in docs/SRS.md; it rejects tables rather than silently producing a broken PDF.
"""
from pathlib import Path
import argparse
import html
import shutil
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--font-dir', type=Path, required=True)
    parser.add_argument('--library-dir', type=Path)
    args = parser.parse_args()
    if args.library_dir:
        sys.path.insert(0, str(args.library_dir.resolve()))
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_CENTER
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.pagesizes import A4
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak

    root = Path(__file__).resolve().parents[2]
    source = root / 'docs' / 'SRS.md'
    target = root / 'SRS.pdf'
    backup = root / 'docs' / 'archive' / 'SRS.v1.0.original.pdf'
    text = source.read_text(encoding='utf-8')
    if any(line.startswith('|') for line in text.splitlines()):
        raise ValueError('SRS source contains a table; extend the renderer first.')
    pdfmetrics.registerFont(TTFont('Arial', str(args.font_dir / 'arial.ttf')))
    pdfmetrics.registerFont(TTFont('ArialBold', str(args.font_dir / 'arialbd.ttf')))
    pdfmetrics.registerFontFamily('Arial', normal='Arial', bold='ArialBold')
    styles = getSampleStyleSheet()
    body = ParagraphStyle('SRSBody', fontName='Arial', fontSize=10, leading=15,
                          spaceAfter=7, textColor=colors.HexColor('#202B36'))
    h1 = ParagraphStyle('SRSTitle', parent=body, fontName='ArialBold', fontSize=22,
                        leading=28, spaceAfter=18, alignment=TA_CENTER)
    h2 = ParagraphStyle('SRSSection', parent=body, fontName='ArialBold', fontSize=15,
                        leading=21, spaceBefore=14, spaceAfter=10, keepWithNext=True)
    h3 = ParagraphStyle('SRSSubsection', parent=h2, fontSize=11, leading=16,
                        spaceBefore=9, spaceAfter=6)
    bullet = ParagraphStyle('SRSBullet', parent=body, leftIndent=12, firstLineIndent=-8)
    story = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith('# '):
            story.append(Spacer(1, 45))
            story.append(Paragraph(html.escape(line[2:]), h1))
        elif line.startswith('## '):
            if line.startswith('## Lịch sử thay đổi') or line.startswith('## 1. '):
                story.append(PageBreak())
            story.append(Paragraph(html.escape(line[3:]), h2))
        elif line.startswith('### '):
            story.append(Paragraph(html.escape(line[4:]), h3))
        elif line.startswith('- '):
            story.append(Paragraph('• ' + html.escape(line[2:]), bullet))
        else:
            story.append(Paragraph(html.escape(line), body))
    def footer(canvas, doc):
        canvas.saveState()
        canvas.setFont('Arial', 8)
        canvas.setFillColor(colors.HexColor('#596779'))
        canvas.drawString(42, 25, 'Alloca SRBS — SRS v1.1 — 2026-10-11')
        canvas.drawRightString(A4[0]-42, 25, f'Trang {doc.page}')
        canvas.restoreState()
    temporary = target.with_suffix('.building.pdf')
    doc = SimpleDocTemplate(str(temporary), pagesize=A4, rightMargin=42,
                            leftMargin=42, topMargin=42, bottomMargin=45,
                            title='Alloca SRS v1.1', author='Alloca project team')
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    if target.exists() and not backup.exists():
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, backup)
    temporary.replace(target)
    print(f'Generated: {target}')
    print(f'Original preserved: {backup}')


if __name__ == '__main__':
    main()
