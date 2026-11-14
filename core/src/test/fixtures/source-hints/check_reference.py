import argparse
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

parser=argparse.ArgumentParser()
parser.add_argument('--url',required=True)
parser.add_argument('--chromium',required=True)
parser.add_argument('--artifacts',required=True)
args=parser.parse_args();artifacts=Path(args.artifacts);artifacts.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=args.chromium,headless=True)
 page=browser.new_page(viewport={'width':1200,'height':1000});errors=[]
 page.on('pageerror',lambda error:errors.append(str(error)))
 rows=page.request.get(args.url+'/decisions.json').json()
 assert len(rows)==8 and sum(row['dimArtwork'] for row in rows)==1
 page.goto(args.url);page.wait_for_function("document.querySelectorAll('article').length===8")
 assert page.locator('#error').text_content()==''
 assert page.locator('article.missing').count()==1
 assert page.locator('article button:disabled').count()==0
 assert page.evaluate("getComputedStyle(document.querySelector('.missing .art')).filter")=='grayscale(1)'
 assert page.evaluate("[...document.querySelectorAll('article:not(.missing) .art')].every(art=>getComputedStyle(art).filter==='none')")
 page.locator('article button').first.click();page.locator('#details').wait_for(state='visible')
 assert 'Open details' in page.locator('#description').text_content()
 page.locator('#details button').click()
 page.screenshot(path=str(artifacts/'mediastorm-native-hints-reference-desktop.png'),full_page=True)
 page.set_viewport_size({'width':390,'height':844})
 assert page.evaluate('document.documentElement.scrollWidth<=window.innerWidth')
 page.screenshot(path=str(artifacts/'mediastorm-native-hints-reference-mobile.png'),full_page=True)
 assert not errors,errors
 result={'passed':True,'exported_kotlin_decisions':len(rows),'only_verified_negative_dimmed':True,'all_reference_cards_selectable':True,'full_status_readable':True,'desktop_mobile_reference_layout':True,'native_rendering_or_remote_focus_verified':False,'policy_reference_only':True,'page_errors':errors}
 (artifacts/'mediastorm-native-hints-reference-results.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps(result));browser.close()
