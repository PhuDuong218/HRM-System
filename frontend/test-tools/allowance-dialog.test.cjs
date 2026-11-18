const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),esbuild=require('esbuild');
test('allowance deactivation waits for reason; cancelling never calls API',async()=>{
 const output=path.join(__dirname,`.allowance-dialog-${process.pid}.mjs`);
 await esbuild.build({entryPoints:[path.join(__dirname,'allowance-dialog-harness.ts')],bundle:true,packages:'external',platform:'node',format:'esm',outfile:output});
 try{const {setup}=await import(pathToFileURL(output).href),f=setup(),row={allowanceTypeId:1,typeName:'Hỗ trợ điện thoại',isActive:true};
  const cancelled=f.page.deactivateType(row);assert.equal(f.calls(),0);f.resolve(null);await cancelled;assert.equal(f.calls(),0);assert.equal(f.page.saving,false);
  const accepted=f.page.deactivateType(row);assert.equal(f.calls(),0);f.resolve('Không còn áp dụng chính sách này');await accepted;assert.equal(f.calls(),1);assert.equal(f.page.saving,true);f.injector.destroy();
 }finally{fs.rmSync(output,{force:true});}
});

test('employee allowance filters include date boundaries and reuse the loaded history', async () => {
  const output = path.join(__dirname, `.allowance-filters-${process.pid}.mjs`);
  await esbuild.build({
    entryPoints: [path.join(__dirname, 'allowance-dialog-harness.ts')],
    bundle: true, packages: 'external', platform: 'node', format: 'esm', outfile: output
  });
  try {
    const { setup } = await import(pathToFileURL(output).href);
    const { page, injector } = setup();
    try {
      page.rows = [
        { employeeAllowanceId: 1, typeCode: 'PHONE', typeName: 'Điện thoại', effectiveFrom: '2026-01-01', effectiveTo: '2026-10-01' },
        { employeeAllowanceId: 2, typeCode: 'PHONE', typeName: 'Điện thoại', effectiveFrom: '2026-10-01', effectiveTo: null },
        { employeeAllowanceId: 3, typeCode: 'MEAL', typeName: 'Ăn trưa', effectiveFrom: '2026-10-01', effectiveTo: null, conditionNote: 'Ca tối' },
        { employeeAllowanceId: 4, typeCode: 'PHONE', typeName: 'Điện thoại', effectiveFrom: '2026-10-02', effectiveTo: null }
      ];
      const visibleIds = () => page.filteredAllowances.map(row => row.employeeAllowanceId);
      page.assignedFilter = { keyword: 'dien thoai', from: '2026-10-01', to: '2026-10-01' };
      page.applyAssignedFilters();
      assert.deepEqual(visibleIds(), [1, 2]);

      page.assignedFilter = { keyword: 'ca toi', from: '2026-10-02', to: '' };
      page.applyAssignedFilters();
      assert.deepEqual(visibleIds(), [3]);

      page.assignedFilter.to = '2026-10-01';
      page.applyAssignedFilters();
      assert.ok(page.assignedFilterError);
      assert.deepEqual(visibleIds(), [3]);

      page.clearAssignedFilters();
      assert.equal(page.assignedFilterError, '');
      assert.deepEqual(visibleIds(), [1, 2, 3, 4]);
    } finally {
      injector.destroy();
    }
  } finally {
    fs.rmSync(output, { force: true });
  }
});
