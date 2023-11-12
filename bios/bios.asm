[org 0x0000]
[bits 16]
[cpu 8086]

KBDSTATUS1 equ 0x417
KBDSTATUS2 equ 0x418

KBDHEAD equ 0x41A
KBDTAIL equ 0x41C
KBDBUF  equ 0x41E

DISK_STATUS equ 0x441
NUM_COLS equ 0x44A

CURPOS_PAGES equ 0x450
ACTIVE_PAGE equ 0x462

HDDNUM  equ 0x475


boot:
	mov cx, cs
	mov ds, cx
    xor cx, cx
	mov es, cx
	mov ss, cx
	mov sp, 0xFFFF
	
	call initVideoMem
	mov di, 0x0000
	mov si, biosBannerStr
	call printStringPreserveColour
	
    call initIvt
    
	call chechIDEPrimaryMaster
    
    ; Params:
    ;   AL - sector count
    ;   CH - cylinder
    ;   CL - sector
    ;   DH - head
    ;   DL - drive
    ;   ES:BX - buffer
    mov al, 1
    mov ah, 2
    mov ch, 0
    mov cl, 1
    xor dh, dh
    mov bx, 0x07C0
    mov es, bx
    xor bx, bx
    mov dl, 0x80
    int 13h
    
	xchg bx, bx
	cmp word [es:bx+510], 0xaa55
	jne .notBootable
	
    jmp 0x07C0:0x0000
    
    xor dl, dl
    
	mov cx, 0xB800
	mov es, cx
	mov ax, 0x0020
	
	mov di, 0x01E2
.loop1:
	cmp ah, 0x80
	jz .endloop1
	stosw
    add ah, 0x10
	jmp .loop1
.endloop1:	
	mov di, 0x0282
.loop2:
	cmp ah, 0x00
	jz .endloop2
	stosw
    add ah, 0x10
	jmp .loop2
.endloop2:
    xor bx, bx
    mov es, bx
	
.hlt:
	nop
	jmp .hlt
	
.notBootable:
	mov si, noBootableDevice
	call printStringWithInt
	jmp .hlt

times 10 int3

smallSleep:
	mov cx, 0xFF
.sleep:
	dec cx
	jcxz .endl
	jmp .sleep
.endl:
	ret
	
times 10 int3

chechIDEPrimaryMaster:
    ; Select drive
	mov dx, 0x1F6
	mov al, 0xA0
	out dx, al
	call smallSleep
	mov dx, 0x1F7
	mov al, 0xEC
	out dx, al
	call smallSleep
	mov dx, 0x1F7
	in al, dx
	cmp al, 0
	jz short .noDevice
.yesDevice:
    inc byte [es:HDDNUM] 
	push es
	push di
	mov di, 0x0000
	mov cx, 0x07C0
	mov es, cx
	mov cx, 256
.readBuffer:
	mov dx, 0x1F0
	in ax, dx
	stosw
	dec cx
	jcxz .endReadBuffer
	jmp .readBuffer
.endReadBuffer:
	pop di
	mov si, primaryIdeSizeStr
	call printStringPreserveColour
	
	mov si, 60 * 2
	mov ax, [es:si]
	mov cl, 1
	shr ax, cl
	mov bx, 10
	call printNum
	
	mov si, strUnitKb
	call printStringPreserveColour
	
	pop es
	ret
.noDevice:
	mov si, noPrimaryIde
	call printStringPreserveColour
	ret

times 10 int3

; Params:
;   AL - sector count
;   CH - cylinder
;   CL - sector
;   DH - head
;   DL - drive
;   ES:BX - buffer
readSectorsFromDrive:
    push di
    test dl, 0x80
    jnz short .harddrive
.floppy:
    jmp $
.harddrive:

    push ax
    mov di, bx
    ; TODO: select drive/controller correctly
    ; So far only the primary master is supported.
    
    ; Sector count in AH
    mov ah, al
    xchg dh, al
    
    ; Select drive
	mov dx, 0x1F6
	or  al, 0xA0
	out dx, al
    
    ; Select sector count
    add dx, 2 - 6
    mov al, ah
    out dx, al
    
    ; Select sector number
    add dx, 3 - 2
    mov al, cl
    out dx, al
    
    ; Cylinder low
    add dx, 4 - 3
    mov al, ch
    out dx, al
    
    ; Cylinder high
    add dx, 5 - 4
    xor al, al
    out dx, al
    
    add dx, 7 - 5
    mov al, 0x20
    out dx, al
    
    times 4 in al, dx
 
.poll:
	in al, dx
	test al, 0x80
	jne short .poll
    test al, 0x08
    je short .poll
	test al, 0x21
	jne short .fail
.sectorReady:
  
    add dx, 0 - 7
    
    mov cx, 256
    
    push ax
.loop:
    in ax, dx
	stosw
    dec cx
    jnz .loop
    
    add dl, 7 - 0
	times 4 in al, dx
    pop ax
    
    dec ah
    jnz short .poll
.success:
    xor ax, ax
    mov es, ax
    pop ax
    ; Clear disk status - no error
    xor ah, ah
    
    mov [es:DISK_STATUS], ah
    pop di
    clc
    ret
.fail:
    mov dh, ah
    xor ax, ax
    mov es, ax
    pop ax
    ; TODO: return undefined error for now
    sub al, dh
    mov ah, 0xBB
    mov [es:DISK_STATUS], ah
    pop di
    stc
    ret

times 10 int3

initIvt:   
    mov word [es:(08h * 4)],     isrTimer
    mov word [es:(08h * 4) + 2], cs
    
    mov word [es:(09h * 4)],     isrKeyboard
    mov word [es:(09h * 4) + 2], cs
    
    mov word [es:(10h * 4)    ], biosVideo
    mov word [es:(10h * 4) + 2], cs
    
    mov word [es:(13h * 4)],     biosBlock
    mov word [es:(13h * 4) + 2], cs
    
    mov word [es:(16h * 4)],     biosKeyboard
    mov word [es:(16h * 4) + 2], cs

	ret
    
isrTimer:
    iret
    
isrKeyboard:
    call getKeyLoop
    
    push ax
    mov al, 0x20
    out 0x20, al 
    pop ax
    
    iret
    
biosVideo:
    push di
    lea di, [.decodeTable]
    call callDecodeTable
    pop di
    iret
.decodeTable:
    dw unimplemented ; Set video mode
    dw unimplemented ; Set cursor type
	dw setCursorPosition ; Set cursor position
	dw unimplemented ; Read cursor position
	dw unimplemented ; Read light pen
	dw unimplemented ; Select active display page
	dw unimplemented ; Scroll active page up
	dw unimplemented ; Scroll active page down
	dw unimplemented ; Read character and attribute at cursor
	dw unimplemented ; Write character and attribute at cursor
	dw unimplemented ; Write character at current cursor
	dw unimplemented ; Set color palette
	dw unimplemented ; Write graphics pixel at coordinate
	dw unimplemented ; Read graphics pixel at coordinate
	dw writeTextTeletype ; Write text in teletype mode
	dw unimplemented ; Get current video state
    
setCursorPosition:
    push es
    mov di, CURPOS_PAGES
    push bx
    mov bl, bh
    xor bh, bh
    shl bx, 1
    add di, bx
    xor bx, bx
    mov es, bx
    mov [es:di], dx
    ; TODO: call videoDriverUpdateCursorPos
    pop bx
    pop es
    ret
    
writeTextTeletype:
    push es
    push bx
    push si
    push ax

    ; Push ax a second time
    push ax
    
    ; Set ax to NUM_COLS
    mov si, NUM_COLS
    mov ax, [es:si]
    
    ; Get the cursor position pointer into si
    mov si, CURPOS_PAGES
    mov bl, bh
    xor bh, bh
    shl bx, 1
    add si, bx
    
    ; Get the cursor positions into bl and bh
    xor bx, bx
    mov es, bx
    mov bx, [es:si]
    
    ; Set bx to columns and di to rows
    xchg bh, bl
    mov di, bx
    and di, 0xFF
    xchg bh, bl
    xor bh, bh
    
    ; Set ax to character index by multiplying row index with NUM_COLS and 
    ; adding column index
    imul di
    add ax, bx
    
    ; Set ax to character offset: TODO, videomodes
    shl ax, 1
    
    ; Store character offset result in di
    mov di, ax
    
    pop ax
   
    ; Load es with the text mode video memory segment
    mov bx, 0xB800
    mov es, bx
    
    ; Store character
    mov [es:di], al
    
    ; Restore es to the BDA segment
    xor ax, ax
    mov es, ax
    
    ; Store the number of columns in al
    ; NOTE: if you change this to ax, remember to change the "Zero the column
    ; index" line too.
    mov di, NUM_COLS
    mov al, [es:di]

    ; Advance the cursor position
    inc byte [es:si]
    ; If the column index is out of range
    cmp byte [es:si], al
    jg .columnsInRange
    ; Zero the column index
    mov [es:si], ah
    ; Increment the row index
    inc byte [es:si + 1]
.columnsInRange:
    
    pop ax
    pop si
    pop bx
    pop es
    ret
    
biosBlock:
    push di
    lea di, [.decodeTable]
    call callDecodeTable
	
	mov di, sp
	jnc .noCarry
.yesCarry:
	or word [di+6], 0x0001
	pop di
    iret
.noCarry:
	and word [di+6], 0xFFFE
	pop di
	iret
	
.decodeTable:
    dw unimplemented ; Reset disk system
    dw unimplemented ; Get status
    dw readSectorsFromDrive
    dw unimplemented ; Write sectors to drive
    dw unimplemented ; Verify sectors
    dw unimplemented ; Format track
    
biosKeyboard:
    push di
    lea di, [.decodeTable]
    call callDecodeTable
    pop di
    iret
.decodeTable:
    dw getKey
	dw getKeystrokeStatus
	dw getShiftStatus
	dw unimplemented ; Set keyboard typematic rate (AT+)
	dw unimplemented ; Keyboard click adjustment (AT+)
	dw unimplemented ; Keyboard buffer write  (AT,PS/2 enhanced keyboards)
	dw unimplemented ; Wait for keystroke and read  (AT,PS/2 enhanced keyboards)
	dw unimplemented ; Get keystroke status  (AT,PS/2 enhanced keyboards)
	dw getShiftStatus2
    
getShiftStatus2:
    mov ah, [es:KBDSTATUS2]
getShiftStatus:
    mov ah, [es:KBDSTATUS1]
    ret
    
getKey:
.waitLoop:
    hlt
    call getKeystrokeStatus
    test ax, ax
    jz .waitLoop
    ret

getKeystrokeStatus:
    xor bh, bh
    mov bl, [es:KBDHEAD]
    mov ax, [es:KBDBUF + bx]
    dec byte [es:KBDHEAD]
    ret
    
unimplemented:
    ret
    
callDecodeTable:
    push cx
    push bx
    push dx
    push es
    push ds
    push ss
    push bp
    
    push bx
    xor bh, bh
    
    mov bl, ah
    add di, bx
    add di, bx
    
    mov bx, cs
    mov ds, bx
    
    pop bx
    
    mov di, [di]
    call di
    
    pop bp
    pop ss
    pop ds
    pop es
    pop dx
    pop bx
    pop cx
    ret

times 10 int3

getKeyLoop:
    jmp .getKeyLoop

; This is here, so short jumps are in range. If this code is placed elsewhere,
; NASM tries to generate near jumps instead, but 8086 does not support them.

; special keys handling
.releaseLShift:
	and byte [es:KBDSTATUS1], 0xFE
	jmp .getcharEnd
.releaseRShift:
	and byte [es:KBDSTATUS1], 0xFD
	jmp .getcharEnd
.releaseCaps:
	; clear caps pressed bit 
	and byte [es:KBDSTATUS2], 0xBF
	jmp .getcharEnd

.getKeyLoop:
	push cx
.keyboardLoop:
	in al, 0x64
	and al, 1
	jz .keyboardLoop
	in al, 0x60
	mov ah, al
	mov bx, ax
	and bx, 0x80
	jz .pressed
.released:
	; check the character code
	cmp ah, 0xB6
	jz short .releaseRShift
	cmp ah, 0xAA
	jz short .releaseLShift
	cmp ah, 0xBA
	jz short .releaseCaps
	jmp .getcharEnd
.pressed:	
    push ax
    mov ax, [es:KBDSTATUS1]
    out 0xe9, ax
    pop ax
    
    ; check if is shifted
	; load KBDSTATUS1 into bh to retrieve info about shifts
	mov bh, [es:KBDSTATUS1]
	and bh, 0x03
	jz .noShift
	mov bh, 1
.noShift:
	; check if caps is active
	mov bl, [es:KBDSTATUS1]
	and bl, 0x40
    
	mov cl, 5
	shr bl, cl
	or bl, bh
	
	; load apropriate table 
    xor bh, bh
    shl bx, 1
    add bx, kbdStateDecode
    mov bx, [bx]
	xlat
    
	call storeChar

	; check the character code
	cmp ah, 0x36
	jz .pressRShift
	cmp ah, 0x2A
	jz .pressLShift
	cmp ah, 0x3A
	jz .pressCaps
	jmp .getcharEnd
.pressCaps:
	; check if caps was already pressed last time
	mov al, [es:KBDSTATUS2]
	and al, 0x40
    
	; if it was, skip toggling
	jnz .notoggle
	
    ; toggle caps active bit
	xor byte [es:KBDSTATUS1], 0x40
    
    mov al, [es:KBDSTATUS1]
    push ax
    mov ah, [es:KBDSTATUS2]
	out 0xe9, ax
    pop ax
.notoggle:
	; set caps pressed bit 
	or byte [es:KBDSTATUS2], 0x40
	jmp .getcharEnd
.pressLShift:
	or byte [es:KBDSTATUS1], 0x01
	jmp .getcharEnd
.pressRShift:
	or byte [es:KBDSTATUS1], 0x02
.getcharEnd:
	pop cx
	ret

times 10 int3

storeChar:
    xor ch, ch
    mov cl, [es:KBDTAIL]
    shl cl, 1
    
    push di
    push si
    lea si, [es:KBDBUF]
    add si, cx
    mov [es:si], ax
   
.updateBuffer:
    sub si, cx
    
    push ds
    push es
    
    xor bx, bx
    mov ds, bx
    
    mov bx, 0xB800
    mov es, bx
    
    mov di, 0
    cld
    mov cx, 16
    rep movsw 
    
    pop es
    pop ds
    
    pop si
    pop di
    
    inc byte [es:KBDTAIL]
    and byte [es:KBDTAIL], 0xf
    
    ret

times 10 int3
	
showBiosBanner:
	mov si, testSuite
	mov di, 0x00A0
	call printStringPreserveColour
	mov di, 0x0140
	mov ax, 0x1234
	mov bx, 16
	call printNum
	mov di, 0x01E0
	mov ax, 0x1234
	mov bx, 10
	call printNum
	mov di, 0x280
	mov ax, 2
	mov bx, 1
	sub ax, bx
	mov bx, 10
	call printNum
	
	mov di, 0x320
	
	; mov ax, -1
	mov ax, 0xFFFF
	; mov bx, 0
	mov bx, 0
	
	mov dx, 1
	cmp ax, bx
	jge .incorrect
	
	mov dx, 2
	cmp ax, bx
	jnae .incorrect
	
	mov si, testsSuccess
	call printStringPreserveColour
	ret
	
.incorrect:
	mov si, testFailed
	call printStringPreserveColour
	
	mov ax, dx
	mov bx, 10
	call printNum
	
	ret
	
times 10 int3

printNum:
	; save the state of registers
	push es
	push dx
	push cx
	mov cx, 0xB800
	mov es, cx
	; output to debug number port
	out 0xE9, ax
	; initialize the counter with 0
	xor cx, cx
	; current number length in cx
	; number base in bx
	; currently evaluated number in ax
.divisionLoop:
	; clear the upper half of the divided number
	xor dx, dx
	; divide ax by number base 
	div bx
	; increment the number of digits
	inc cx
	; push the remainder of dividing by base to the stack 
	push dx
	; if there's no number to be divided next, end the division loop
	cmp ax, 0
	jz .endDivLoop
	; loop
	jmp .divisionLoop
.endDivLoop:
	; now, we have the digits (modulo by base) of our number on the stack
	; first we divided by base, then base*base, then base*base*base etc.
	; therefore the first one popped off the stack is the last digit
.printLoop:
	; if no characters are left, jmp to end
	jcxz .endPrintLoop
	; get the next number into ax
	pop ax
	; convert the number to ASCII 
	cmp al, 10
	jb .add0c
	add al, ('A' - 10)
	jmp .overAdd0c
.add0c:
	add al, '0'
.overAdd0c:
	; print the character 
	mov [es:di], al
	; move di 2 bytes (1 byte for character + 1 byte for color)
	add di, 2 
	; decrement the number of characters left
	dec cx
	; loop
	jmp .printLoop
.endPrintLoop:
	; restore registers
	pop cx
	pop dx
	pop es
	ret

times 10 int3
	
printStringPreserveColour:
	push es
	mov ax, 0xB800
	mov es, ax
.printLoop:
	; load [ds:si] to al 
	lodsb
	cmp al, 0
	jz .endPrintString
	; write al to [es:di]
	stosb
	; output to debug character port
	out 0xE8, al
	; skip color code
	inc di
	; loop
	jmp .printLoop
.endPrintString:
	pop es
	ret
	
printStringWithInt:
.loop:
	lodsb
	test al, al
	jz .endloop
	mov ah, 0x0E
	out 0xE8, al
	int 10h
	jmp printStringWithInt.loop
.endloop:
	ret
	
times 10 int3
	
initVideoMem:
	cld
	mov cx, 0xB800
	mov es, cx
	mov di, 0x0000
	mov al, ' '
	mov ah, 0x07
	mov cx, 80*25
	rep stosw
    
    mov cx, 0x0000
    mov es, cx
    mov word [es:NUM_COLS], 80
    
    mov di, CURPOS_PAGES
    mov cx, 8
    xor ax, ax
    rep stosw
    
	ret

times 10 int3

kbdStateDecode:
    dw scancodeNormalToAscii
    dw scancodeShiftToAscii
    dw scancodeCapsToAscii
    dw scancodeCapsShiftToAscii

biosBannerStr: db "NNX OC86BIOS, alpha v0.1.0.6, "
testSuite: db "Debug/test build",0
testFailed: db "Test failed: ",0
testsSuccess: db "Tests passed",0
noPrimaryIde: db "No primary IDE",0
noBootableDevice: db "No bootable device",0
primaryIdeSizeStr: db "Primary IDE size: ",0
strUnitKb: db "KB",0
scancodeNormalToAscii:
.start:
db 0 ; null
db 0 ; esc
db "1234567890-="
db 0 ; backspace
db 0 ; tab
db "qwertyuiop[]"
db 0xA ; enter
db 0 ; left ctrl
db "asdfghjkl;'`"
db 0 ; left shift
db 0x5C ; backslash
db "zxcvbnm,./"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.start - .end) db 0
scancodeCapsToAscii:
.start:
db 0 ; null
db 0 ; esc
db "1234567890-="
db 0 ; backspace
db 0 ; tab
db "QWERTYUIOP[]"
db 0xA ; enter
db 0 ; left ctrl
db "ASDFGHJKL;'`"
db 0 ; left shift
db 0x5C ; backslash
db "ZXCVBNM,./"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.start - .end) db 0
scancodeShiftToAscii:
.start:
db 0 ; null
db 0 ; esc
db "!@#$%^&*()_+"
db 0 ; backspace
db 0 ; tab
db "QWERTYUIOP{}"
db 0xA ; enter
db 0 ; left ctrl
db 'ASDFGHJKL:"~'
db 0 ; left shift
db "|ZXCVBNM<>?"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.start - .end) db 0
scancodeCapsShiftToAscii:
.start:
db 0 ; null
db 0 ; esc
db "!@#$%^&*()_+"
db 0 ; backspace
db 0 ; tab
db "qwertyuiop{}"
db 0xA ; enter
db 0 ; left ctrl
db 'asdfghjkl:"~'
db 0 ; left shift
db "|zxcvbnm<>?"
db 0 ; right shift
db "*"
db 0 ; left alt
db " " ; space
db 0 ; caps lock
times 10 db 0 ; F1 - F10
db 0 ; numlock
db 0 ; scroll lock
db "789-456+1230."
.end:
times 128 - (.start - .end) db 0

times (0xFFF0) - ($ - $$) hlt
jmp 0xF000:0x0000
times (0x10000) - ($ - $$) hlt